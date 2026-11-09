# 微信流 Android 版（MVP，开发中）

把手机微信里的聊天交给 AI 应用，或沉淀成 Markdown 笔记。和桌面版一样：只处理你主动分享或粘贴的内容，不读微信数据库、不用无障碍服务、不注入微信，应用本身没有联网权限。

## 怎么把内容送进微信流

| 入口 | 怎么用 |
|---|---|
| 系统分享 | 在微信或任意应用里点「分享」，选 **微信流**（直接选目标发送）或 **分批收集到微信流**（先攒着）。 |
| 用其他应用打开 | 在微信里打开 ZIP、TXT、图片或视频，选「用其他应用打开」→ 微信流。 |
| 剪贴板 | 在微信里复制消息，回到微信流首页点「从剪贴板粘贴」，可以在弹窗里修改后再发送或加入收集。 |

能处理的内容：

- **ZIP**：按桌面版规则解析。优先读取 `聊天记录.txt`，否则取消息条数最多的 TXT；其余文件（图片、视频等）作为附件。条目数、解压大小、路径都会先校验，异常的 ZIP 原样作为附件发送。
- **TXT / 文字**：符合微信导出格式（`·发送人` + `2026年3月5日 09:15` + 正文）时按消息解析，可以和其他批次按时间合并去重；否则作为普通文本。UTF-8 读不通时按 GB18030 再试一次。
- **图片、视频及其他文件**：作为附件。

## 发送

收到内容后进入发送页：

1. **聊天名**（可选）：用作笔记标题和记录里的名称。安卓上读不到微信标题栏，所以由你填写；不填时按参与人自动命名（「张三与李四的聊天」）。
2. **场景**：选一个场景提示词，或「不附加」。默认是「群聊总结」，上次的选择会被记住。
3. **附带图片和视频**：开关决定附件是否一起发出；聊天文本总会发送。
4. **目标**：

| 目标 | 行为 |
|---|---|
| ChatGPT / Codex、Claude、豆包、千问、DeepSeek、WorkBuddy | 先把「场景提示词 + 聊天记录」写入剪贴板，再用 `ACTION_SEND`（指定包名）把文本和附件交给该应用。应用不接收附件时退回只发文本；完全不接收分享时直接打开应用，提示长按粘贴。未安装时提示，剪贴板内容保留。 |
| 沉淀到 Obsidian | 通过 SAF 选择知识库目录，写入 `子文件夹/聊天名.md` 和 `子文件夹/附件/`，完成后可一键在 Obsidian 中打开。 |
| 沉淀到文件夹 | 同上，写入任意你选择的目录。 |
| 复制到剪贴板 | 只复制文本。 |
| 其他应用 | 打开系统分享面板。 |

聊天记录超过 10 万字时，不再塞进 Intent 文本（Binder 有约 1 MB 的上限），而是作为 `聊天记录.txt` 附件发送，文本里只放提示词和说明。

## 分批收集

微信一次最多转发 100 条。每批都分享到「分批收集到微信流」，会进入同一个收集，并显示已收批次、消息数和最后一条消息，方便回微信找到下一批的起点。收齐后点「完成并发送」，一次选目标和场景；多批里的微信格式聊天会按时间合并并去掉重叠部分。收集可以暂停、在记录页继续，发送中断后会标记为「待重试」。

## 本地记录

「记录」页列出所有批次和收集：状态（未发送 / 已发送 / 已复制 / 失败）、目标、场景和结果说明，支持重新发送、复制聊天文本、删除。

## 场景

内置「群聊总结」（安卓新增，默认）、「日常摘要」「客户复盘」「项目周会」，后三个的 ID 和输出规范与 macOS / Windows 一致。可以新建、编辑、删除、设默认，也可以「恢复内置场景」。桌面版的「公众号文章提取」「视频信息读取」依赖技能包，安卓暂无技能库，没有内置；场景里写的 `{{skill:id}}` 会按桌面版「技能不可用」的措辞渲染成文字。

## 与桌面版的差异

| 方面 | 桌面版（macOS / Windows） | Android 版 |
|---|---|---|
| 输入 | 微信「合并转发 → 转发到其他应用」生成 ZIP | 系统分享、「用其他应用打开」、剪贴板粘贴（安卓微信不能把合并转发导出成 ZIP 给第三方应用） |
| 分享入口 | 十二个 Share Extension / Share Target | 两个：「微信流」「分批收集到微信流」，目标在应用内选择 |
| 交给 AI | 激活目标应用并自动粘贴 | `ACTION_SEND` 指定包名 + 剪贴板兜底，没有自动粘贴 |
| 群名识别 | 读取微信标题栏（AX / OCR / UIA） | 手动填写聊天名，留空按参与人命名 |
| 群与场景绑定、场景快捷键、技能库 | 有 | 暂无 |
| Obsidian / 文件夹 | 直接写路径 | SAF 选目录后写入；笔记格式与合并规则与 `KnowledgeDelivery` 一致，可与桌面版互相追加 |
| `intent.json`（90 秒转发意图） | 分享扩展写入，主程序消费 | 不使用：分享页本身就在前台让你选目标 |
| 语言 | 应用内切换 | 跟随系统；Android 13+ 可在系统设置里单独选择 |

## 数据存放

全部在应用私有目录，`allowBackup=false` 且排除设备迁移：

```text
files/
├── Inbox/                  shared/README.md 的 Inbox 契约
│   ├── Staging/<id>.staging/   导入时先写这里
│   ├── Ready/<id>/             manifest.json · state.json · files/
│   ├── Failed/<id>.json
│   ├── Logs/android.log
│   └── collections.json    分批收集账本
└── scenes.json
cache/work/                 解压出的媒体和长文本附件，24 小时后清理
```

`manifest.json` / `state.json` 的字段名与 macOS 一致（`batchID`、`createdAt` 为 ISO-8601 UTC `Z`、`action` 取值同 `ShareAction`）；`state.json` 多一个安卓专用的 `targetId`。

## 代码结构

```text
android/
├── core/   纯 Kotlin/JVM，不依赖 Android：ZIP 解析、聊天解析与合并、Inbox、
│           分批收集账本、场景、发送文本规划、Markdown 笔记与合并（含 JUnit 测试）
└── app/    Jetpack Compose 界面、分享接收、Intent 投递、SAF 写入
```

Agent logo 直接从 `shared/AppLogos` 打包为 assets（见 `app/build.gradle.kts`），启动图标由 `shared/Design/BrandMark.png` 生成。

## 构建

需要 JDK 17 和 Android SDK（compileSdk 35）。

```bash
cd android
./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug
```

仓库里暂时没有 `gradle/wrapper/gradle-wrapper.jar`（编写时的环境无法下载）。第一次本地构建前，用 Gradle 8.11.1 运行一次 `gradle wrapper --gradle-version 8.11.1` 生成它，或直接用 Android Studio 打开 `android/`。CI（`.github/workflows/android.yml`）在缺少 JAR 时会用 `gradle/actions/setup-gradle` 安装的 Gradle 自动生成，然后跑单元测试、构建 debug APK 并上传为 artifact。

## 待核实

AI 应用包名在 2026-11-08 查询，尚未在真机上逐个验证：

| 目标 | 包名 | 来源 |
|---|---|---|
| ChatGPT / Codex | `com.openai.chatgpt` | Google Play（Codex 没有独立安卓应用，在 ChatGPT 内） |
| Claude | `com.anthropic.claude` | Google Play |
| DeepSeek | `com.deepseek.chat` | Google Play |
| 豆包 | `com.larus.nova` | 小米应用商店、应用宝 |
| 千问 | `com.aliyun.tongyi` | 小米应用商店（原「通义千问」，改名后包名未变） |
| WorkBuddy | `com.tencent.workbuddy.app` | 小米应用商店页面地址，仅见于搜索结果 |
| Obsidian | `md.obsidian` | Google Play |

WeSight、DeepSeek Harness 只有桌面版，安卓没有对应入口。

各应用对 `ACTION_SEND` 的实际处理也需要真机验证：是否接收 `EXTRA_TEXT`（iOS 实测豆包会丢掉分享的文字、千问对「文字 + 文件」报格式不支持）、是否接收多附件、是否接收 TXT 附件。剪贴板兜底就是为这些情况准备的。

## v0.2 界面

v0.2.0（versionCode 2）采用黑白灰层级、轻量记录列表和胶囊导航，绿色保留为品牌与状态点。主页的底部入口打开可编辑的粘贴面板；投递页先选场景和目标，再通过底部按钮确认。聊天预览可展开，场景编辑改为底部面板；键盘、系统返回与安全区使用 Android / Compose 原生行为。页面淡入约 160–220 ms，跟随系统动画时长设置，没有循环装饰动画。

视觉参考来自 [Mobbin 的 Grok Bot 公开预览](https://mobbin.com/apps/grok-bot-ios-c5e50da8-b7fc-423a-a264-5ea9c387d5ad)，可见资料为静态画面；动效为 Android 原生实现。系统深色模式和编辑中的投递选择均会保留，配置变更后仍可继续发送。

Android 工作流除 43 项 core 单元测试和 debug APK 外，还在 API 35 模拟器自动操作粘贴、键盘、聊天预览、复制投递、记录与新增场景，输出浅色、深色和 320 dp / 1.3 倍字体截图。下载 run 中的 `android-ui-smoke` artifact 查看 PNG、instrumentation 报告和 `gfxinfo.txt`。模拟器检查不代表真机帧率或第三方应用分享兼容性保证。本机不安装 SDK 或 Gradle。

CI debug APK 的签名由 runner 生成；即使版本号递增，不同 run 也可能不能覆盖安装。是否可直接升级应比较签名证书；本版本不引入发布密钥。
