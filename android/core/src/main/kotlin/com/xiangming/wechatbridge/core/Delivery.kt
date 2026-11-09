package com.xiangming.wechatbridge.core

enum class TargetKind { AI_APP, CLIPBOARD, FOLDER, OBSIDIAN, SYSTEM_SHARE }

/**
 * A destination on the send screen. For AI apps, [packageName] is the app the
 * share intent is addressed to; [logoAsset] names a file in `shared/AppLogos`,
 * which the app module packages as assets.
 */
data class DeliveryTarget(
    val id: String,
    val kind: TargetKind,
    val zhName: String,
    val enName: String,
    val packageName: String? = null,
    val logoAsset: String? = null,
) {
    fun displayName(lang: Lang): String = if (lang == Lang.ZH) zhName else enName
}

/**
 * Package names were looked up on 2026-11-08 and have not been tried on a
 * device yet (see android/README.zh-CN.md):
 *
 * - ChatGPT `com.openai.chatgpt`, Claude `com.anthropic.claude`, DeepSeek
 *   `com.deepseek.chat`: Google Play listings.
 * - 豆包 `com.larus.nova`, 千问 `com.aliyun.tongyi`, WorkBuddy
 *   `com.tencent.workbuddy.app`: Xiaomi / Tencent app store listings.
 * - Obsidian `md.obsidian`: Google Play listing.
 *
 * Codex has no Android app of its own; it lives inside ChatGPT. WeSight and
 * DeepSeek Harness are desktop-only, so they have no Android entry.
 */
object TargetCatalog {
    val chatGpt = DeliveryTarget("chatgpt", TargetKind.AI_APP, "ChatGPT / Codex", "ChatGPT / Codex", "com.openai.chatgpt", "04-chatgpt.png")
    val claude = DeliveryTarget("claude", TargetKind.AI_APP, "Claude", "Claude", "com.anthropic.claude", "03-claude.png")
    val doubao = DeliveryTarget("doubao", TargetKind.AI_APP, "豆包", "Doubao", "com.larus.nova", "01-doubao.png")
    val qwen = DeliveryTarget("qwen", TargetKind.AI_APP, "千问", "Qwen", "com.aliyun.tongyi", "02-qwen.png")
    val deepSeek = DeliveryTarget("deepseek", TargetKind.AI_APP, "DeepSeek", "DeepSeek", "com.deepseek.chat")
    val workBuddy = DeliveryTarget("workbuddy", TargetKind.AI_APP, "WorkBuddy", "WorkBuddy", "com.tencent.workbuddy.app", "06-workbuddy.png")
    val clipboard = DeliveryTarget("clipboard", TargetKind.CLIPBOARD, "复制到剪贴板", "Copy to clipboard")
    val folder = DeliveryTarget("folder", TargetKind.FOLDER, "沉淀到文件夹", "Save to folder")
    val obsidian = DeliveryTarget("obsidian", TargetKind.OBSIDIAN, "沉淀到 Obsidian", "Save to Obsidian", "md.obsidian", "05-obsidian.png")
    val systemShare = DeliveryTarget("share", TargetKind.SYSTEM_SHARE, "其他应用", "Other apps")

    val all: List<DeliveryTarget> = listOf(
        chatGpt, claude, doubao, qwen, deepSeek, workBuddy, obsidian, folder, clipboard, systemShare,
    )

    fun byId(id: String?): DeliveryTarget? = all.firstOrNull { it.id == id }
}

/** What one hand-off carries. */
data class DeliveryPlan(
    /** Goes in `EXTRA_TEXT`. */
    val text: String,
    /** Written to the clipboard first, so any failure is one paste away from done. */
    val clipboardText: String,
    /** Non-null when the chat is too long for an intent extra and travels as a TXT instead. */
    val transcriptFileText: String?,
    val attachments: List<Attachment>,
)

object DeliveryPlanner {
    /**
     * Intent extras share the ~1 MB binder buffer with everything else in the
     * transaction, and strings travel as UTF-16. 100k characters leaves ample
     * room for the attachment URIs.
     */
    const val INLINE_TEXT_LIMIT = 100_000
    const val TRANSCRIPT_FILE_NAME = "聊天记录.txt"

    fun compose(prompt: String?, chatText: String, lang: Lang): String {
        val instruction = prompt?.trim().orEmpty()
        val chat = chatText.trim()
        if (instruction.isEmpty()) return chat
        if (chat.isEmpty()) return instruction
        val heading = when (lang) {
            Lang.ZH -> "以下是聊天记录："
            Lang.EN -> "Chat transcript:"
        }
        return "$instruction\n\n$heading\n$chat"
    }

    fun plan(
        material: Material,
        prompt: String?,
        includeAttachments: Boolean,
        lang: Lang,
        inlineLimit: Int = INLINE_TEXT_LIMIT,
    ): DeliveryPlan {
        val attachments = if (includeAttachments) material.attachments else emptyList()
        val chat = material.chatText
        if (chat.length <= inlineLimit) {
            val text = compose(prompt, chat, lang)
            return DeliveryPlan(text, text, null, attachments)
        }
        val note = when (lang) {
            Lang.ZH -> "聊天记录较长，已作为附件「$TRANSCRIPT_FILE_NAME」一并发送，请读取附件。"
            Lang.EN -> "The chat is long, so it is attached as “$TRANSCRIPT_FILE_NAME”. Please read the attachment."
        }
        val text = listOfNotNull(prompt?.trim()?.takeIf { it.isNotEmpty() }, note).joinToString("\n\n")
        return DeliveryPlan(text, text, chat, attachments)
    }
}
