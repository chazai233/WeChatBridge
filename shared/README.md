# shared — 两端共用的资产与契约

这个目录放的是 **macOS、Windows（以及 iOS、Android）都会消费的东西**。改这里之前记住：各端的发布链路都会把下面这些内容打进各自的包。

| 目录 | 谁在用 |
|---|---|
| `AppLogos/` | Agent logo：macOS 打包进 `.app`，Windows 发布到 `Assets/AppLogos`（`AppLogos.cs`），Android 直接作为 assets 打包（`android/app/build.gradle.kts`） |
| `Design/` | 品牌源图（`BrandMark.png` 等），两端的应用图标都由它生成 |
| `Screenshots/` | macOS 参考截图——Windows UI 移植的对照基准 |
| `Skills/` | `catalog.json` + 技能包；macOS `SkillLibrary` 与 Windows `SkillService` 共用 |

## Inbox 共享契约（两端必须一致）

```text
%LOCALAPPDATA%\WeChatBridge\Inbox        Windows（macOS 用 App Group 容器）
├── Ready/<batchId>/
│   ├── manifest.json    schemaVersion=1，createdAt = ISO-8601 UTC Z
│   ├── intent.json      一次性转发意图（90 秒新鲜度窗口）
│   ├── state.json       投递结果回写（delivered/failed/expired/copied）
│   └── files/           原子提交：先 staging 再整体 rename
├── Failed/
└── Logs/
```

- 分享侧（Share Extension / ShareTarget.exe）只负责：校验 ZIP → 原子写入 → 剪贴板兜底 → 唤醒主程序。**不做投递**。
- intent 一次性：被消费即删除；过期（>90s）记 `expired` 不裸发。
- 剪贴板在投递前写入兜底内容，任何失败都让用户离一次 Ctrl+V 只有一步。
- Android 把 Inbox 放在应用私有目录 iles/Inbox，manifest.json / state.json 字段与上面一致；分享页本身在前台选择目标，所以不写 intent.json。

## 文件级端口对照表

按模块对齐两端实现——看一边代码时，另一边对应文件就是这张表：

| 模块 | macOS（`macos/`） | Windows（`windows/`） | Android（`android/`） |
|---|---|---|---|
| 分享接收 | `Sources/WeChatBridgeShare/`（Share Extension） | `src/WeChatBridge.ShareTarget/`（Share Target exe，稀疏包注册） | `app/.../ui/ShareActivity.kt` + `data/IncomingIntents.kt`（两个 activity-alias 分享入口，含「用其他应用打开」） |
| Inbox 模型 | `WeChatBridgeCore/Inbox*.swift` | `WeChatBridge.Windows.Core/InboxModels.cs` `InboxReader.cs` | `core/.../Inbox.kt` `BatchModels.kt`（应用私有目录，同一套 manifest/state） |
| 转发编排 | `WeChatBridgeApp/ActionRunner.swift`（含 pending 串行化） | `MainViewModel.PerformForward` + `EnqueueForward` | `deliver/Deliverer.kt` |
| 激活+粘贴 | `AutoPaste.swift`（AX 权限、窗口恢复、⌘V） | `Delivery/DeliveryEngine.cs` + `Interop/Win32.cs`（SetForegroundWindow / SendInput） | 不移植：`ACTION_SEND` 指定包名 + 剪贴板兜底（`Deliverer.kt`） |
| 目标应用表 | `bundleIdentifier` + Launch Services | `Delivery/WindowsForwardTarget.cs`（进程名/exe 路径/AUMID 三线索） | `core/.../Delivery.kt` `TargetCatalog`（安卓包名） |
| 剪贴板 | `FilePasteboard.swift` | `Delivery/WindowsClipboard.cs`（CF_HDROP / CF_UNICODETEXT，STA） | `Deliverer.copyToClipboard`（纯文本） |
| 豆包附件 | `DoubaoAttachment.swift` | `WindowsForwardTarget.ReadsLocalArchives`（豆包+千问；ZIP 走路径文本） | —（附件随 `EXTRA_STREAM` 发送，不接收时退回纯文本） |
| Obsidian | `KnowledgeDelivery.swift` | `Obsidian/`（vault 直写） | `core/.../KnowledgeNote.kt` `KnowledgeDelivery.kt` + `deliver/DocumentNoteFolder.kt`（SAF） |
| 文件夹归档 | `KnowledgeDelivery.swift` | `Obsidian/KnowledgeDelivery.cs` + `DeliveryFolderPath`（无需 Obsidian，多级子文件夹） | 同上（SAF 选目录） |
| 分批收集 | `BatchCollection.swift` `BatchCollectionPanel.swift` | `Core/Collections/` + `CollectionWindow`（冻结、恢复、撤销、统一交付） | `core/.../Collections.kt` + `ShareActivity` 收集页 |
| 本地化 | en / zh-Hans `.strings` | `Core/Localization/` + `LocExtension`（中文、English、跟随系统） | `res/values` `res/values-zh`（跟随系统，Android 13+ 可单独选） |
| 群名识别 | `WeChatTitleReader.swift`（AX 标签 / 截屏 OCR） | `Services/WeChatUiTitleReader.cs`（UIA 读 mmui::MainWindow） | 不移植：发送页手动填写聊天名 |
| 场景决策 | `SceneCoordinator.swift` `SceneResolver` `Preferences.swift` | `Services/SceneService.cs` + `Core/Scenes/` | `core/.../Scenes.kt`（无群绑定，发送时手选） |
| 场景快捷键 | `SceneShortcutController.swift`（⌃⌥1–9） | `SceneShortcutController.cs`（Ctrl+Alt+1–9） | — |
| 批次洞察 | `WeChatBatchInsights.swift` | `Archive/WeChatBatchInsightsReader.cs` | `core/.../Material.kt` `BatchInsights` |
| 技能库 | `SkillLibrary.swift` | `Services/SkillService.cs` + `Core/Skills/` | —（`{{skill:id}}` 按「技能不可用」渲染） |
| 菜单栏/托盘 | `StatusItemController.swift` | `TrayIconService.cs`（纯 Win32 NotifyIcon） | — |
| 目标/场景选择浮层 | `TargetPickerPanel.swift` `ScenePickerPanel.swift` | `TargetPickerWindow` `ScenePickerWindow` | `ui/SendScreen.kt` |
| 首次引导 | `Onboarding/` | `Onboarding/` | 首页「怎么用」卡片 |
| 设置界面 | `Views/Settings/*.swift` | `Panes/*.xaml`（六页，顺序对齐） | `ui/SettingsScreen.kt` `ScenesScreen.kt` `HistoryScreen.kt` |
| 设计 token | `Design/Theme.swift` | `App.xaml` 资源字典（色值逐项移植） | `ui/theme/Theme.kt`（主色 #07C160） |
| Toast | `Feedback/Toast.swift` | `MainWindow` 胶囊 + 托盘气泡兜底 | 系统 Toast |
| 更新 | Sparkle | MSIX + SignPath（`windows-sign.yml`） | —（CI 产出 debug APK） |

## 有意不移植的部分

- **辅助功能/屏幕录制权限页**——Windows 不需要这些授权。
- **自动更新**——Windows 走 MSIX 发布链路，不引 Sparkle 等价物。
- **豆包非 ZIP 上传器**——微信导出永远是 ZIP；ZIP 已走 pathOnly 捷径。

功能核对、上游合并范围和实机验证项目见 `windows/docs/MACOS_PARITY.zh-CN.md`。收集请求只追加原始文件，不因 90 秒转发窗口过期而丢弃；完成时才冻结并串行交付。未完成收集的文件受保护，不参与普通历史清理。
