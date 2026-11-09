package com.xiangming.wechatbridge.core

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Turns chat text into a self-contained Markdown note. Port of macOS
 * `ObsidianNote`: the same front matter, the same `**sender** · time` record
 * headers and the same merge rule, so a note written on Android can be
 * extended from a Mac and the other way round.
 */
object KnowledgeNote {
    sealed interface MergeOutcome {
        /** Not a note this app wrote, or nothing to merge by. Write a new note instead. */
        data object NotApplicable : MergeOutcome
        data object NothingNew : MergeOutcome
        data class Merged(val markdown: String) : MergeOutcome
    }

    const val ATTACHMENTS_FOLDER = "附件"

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
    private val recordHeader = Regex("(?m)^\\*\\*(.+?)\\*\\* · (\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})$")

    /**
     * The chat name the user typed wins; transcript participants are only a
     * fallback, then [fallbackName].
     */
    fun title(chatName: String?, records: List<TranscriptRecord>?, fallbackName: String): String {
        val name = chatName?.trim().orEmpty()
        if (name.isNotEmpty()) return if (name.endsWith("的聊天")) name else "${name}的聊天"
        val participants = records.orEmpty().map { it.sender.trim() }.filter { it.isNotEmpty() }.distinct()
        return when (participants.size) {
            0 -> fallbackName
            1 -> "${participants[0]}的聊天"
            2 -> "${participants[0]}与${participants[1]}的聊天"
            else -> "${participants[0]}等${participants.size}人的聊天"
        }
    }

    /**
     * @param attachments original name → name saved under `附件/`, for linking
     *   a record that mentions the file.
     * @param looseAttachments saved names no record mentions, listed at the end.
     */
    fun render(
        title: String,
        chatName: String?,
        sceneName: String?,
        createdAt: LocalDateTime,
        records: List<TranscriptRecord>?,
        body: String?,
        archiveNames: List<String>,
        attachments: Map<String, String> = emptyMap(),
        looseAttachments: List<String> = emptyList(),
    ): String {
        val lines = mutableListOf("---", "title: ${quoted(title)}", "source: WeChat")
        if (!chatName.isNullOrEmpty()) lines += "chat: ${quoted(chatName)}"
        if (!sceneName.isNullOrEmpty()) lines += "scene: ${quoted(sceneName)}"
        lines += "exported: ${stamp.format(createdAt)}"
        if (records != null) lines += "messages: ${records.size}"
        archiveNames.firstOrNull()?.let { lines += "archive: ${quoted("$ATTACHMENTS_FOLDER/$it")}" }
        lines += "---"
        lines += ""
        lines += "# $title"
        lines += ""
        lines += if (archiveNames.isEmpty()) {
            "> 来源：微信"
        } else {
            "> 来源：微信 · 原始归档：" + archiveNames.joinToString("、") { "[[$ATTACHMENTS_FOLDER/$it]]" }
        }

        val referenced = mutableSetOf<String>()
        when {
            !records.isNullOrEmpty() -> {
                lines += ""
                lines += "## 聊天记录"
                for (record in records) {
                    lines += ""
                    lines += "**${record.sender}** · ${stamp.format(record.time)}"
                    lines += ""
                    lines += record.text
                    for (saved in referencedAttachments(record.text, attachments)) {
                        referenced += saved
                        lines += ""
                        lines += attachmentLink(saved)
                    }
                }
            }
            !body.isNullOrBlank() -> {
                lines += ""
                lines += "## 聊天记录"
                lines += ""
                lines += body.trim()
            }
            archiveNames.isNotEmpty() -> {
                lines += ""
                lines += "未能从原始归档中解析聊天文本。原始 ZIP 已保留，可在附件中打开。"
            }
        }

        val loose = looseAttachments.filterNot { it in referenced }
        if (loose.isNotEmpty()) {
            lines += ""
            lines += "## 附件"
            for (name in loose) {
                lines += ""
                lines += attachmentLink(name)
            }
        }
        lines += ""
        return lines.joinToString("\n")
    }

    /**
     * Adds only records that are not already in a note this app wrote. A
     * user's unrelated same-named note is never rewritten.
     */
    fun merge(
        existingMarkdown: String,
        records: List<TranscriptRecord>,
        attachments: Map<String, String>,
        archiveNames: List<String>,
        chatName: String?,
        sceneName: String?,
        mergedAt: LocalDateTime,
    ): MergeOutcome {
        if (records.isEmpty()) return MergeOutcome.NotApplicable
        val markdown = existingMarkdown.trimStart('\uFEFF').replace("\r\n", "\n")
        if (!markdown.startsWith("---\n")) return MergeOutcome.NotApplicable
        val closing = markdown.indexOf("\n---", startIndex = 4)
        if (closing < 0) return MergeOutcome.NotApplicable
        val frontMatter = markdown.substring(4, closing).split("\n").toMutableList()
        if ("source: WeChat" !in frontMatter) return MergeOutcome.NotApplicable

        val matches = recordHeader.findAll(markdown).toList()
        val known = HashSet<String>()
        matches.forEachIndexed { index, match ->
            val bodyEnd = if (index + 1 < matches.size) matches[index + 1].range.first else markdown.length
            known += messageKey(
                match.groupValues[1],
                match.groupValues[2],
                markdown.substring(match.range.last + 1, bodyEnd),
            )
        }
        val fresh = records.filter { messageKey(it.sender, stamp.format(it.time), it.text) !in known }
        if (fresh.isEmpty()) return MergeOutcome.NothingNew

        fun upsert(name: String, line: String) {
            val index = frontMatter.indexOfFirst { it.startsWith("$name:") }
            if (index >= 0) frontMatter[index] = line else frontMatter += line
        }
        upsert("exported", "exported: ${stamp.format(mergedAt)}")
        upsert("messages", "messages: ${matches.size + fresh.size}")
        if (!chatName.isNullOrEmpty() && frontMatter.none { it.startsWith("chat:") }) {
            frontMatter += "chat: ${quoted(chatName)}"
        }
        if (!sceneName.isNullOrEmpty() && frontMatter.none { it.startsWith("scene:") }) {
            frontMatter += "scene: ${quoted(sceneName)}"
        }

        val result = StringBuilder()
        result.append("---\n").append(frontMatter.joinToString("\n")).append("\n---\n")
        result.append(markdown.substring(closing + 4).trim('\n'))
        val source = if (archiveNames.isEmpty()) "" else archiveNames.joinToString("、") { attachmentLink(it) }
        result.append("\n\n> 追加归档：").append(source).append("（").append(stamp.format(mergedAt)).append("）\n")
        for (record in fresh) {
            result.append("\n**").append(record.sender).append("** · ").append(stamp.format(record.time))
            result.append("\n\n").append(record.text).append("\n")
            for (saved in referencedAttachments(record.text, attachments)) {
                result.append("\n").append(attachmentLink(saved)).append("\n")
            }
        }
        return MergeOutcome.Merged(result.toString())
    }

    /** Saved names of attachments a record mentions, longest original name first. */
    fun referencedAttachments(text: String, available: Map<String, String>): List<String> {
        var remaining = text
        val result = mutableListOf<String>()
        val names = available.keys.sortedWith(
            compareByDescending<String> { it.toByteArray(Charsets.UTF_8).size }.thenBy { it },
        )
        for (name in names) {
            if (name.isEmpty() || !remaining.contains(name)) continue
            result += available.getValue(name)
            remaining = remaining.replace(name, "")
        }
        return result
    }

    fun attachmentLink(name: String): String {
        val path = "$ATTACHMENTS_FOLDER/$name"
            .replace("\\", "\\\\")
            .replace("[", "\\[")
            .replace("]", "\\]")
            .replace("#", "\\#")
            .replace("^", "\\^")
            .replace("|", "\\|")
        return if (MediaKind.of(name).embeds) "![[$path]]" else "[[$path]]"
    }

    private fun messageKey(sender: String, stamp: String, body: String): String {
        val content = body.replace("\r\n", "\n")
            .split("\n")
            .map { it.trim() }
            .filter {
                it.isNotEmpty() &&
                    !it.startsWith("[[") &&
                    !it.startsWith("![[") &&
                    !it.startsWith("> 追加归档：")
            }
            .joinToString("\n")
        return "${sender.trim()}\n$stamp\n$content"
    }

    private fun quoted(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}
