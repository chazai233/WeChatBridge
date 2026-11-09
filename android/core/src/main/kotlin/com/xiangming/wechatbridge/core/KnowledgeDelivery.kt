package com.xiangming.wechatbridge.core

import java.io.File
import java.io.IOException
import java.time.LocalDateTime

/**
 * A folder the note is written into. The app implements it over SAF
 * (`DocumentFile`); tests and any desktop tooling use [FileNoteFolder].
 */
interface NoteFolder {
    val name: String
    fun childNames(): Set<String>
    /** The child folder, created when absent. */
    fun folder(name: String): NoteFolder
    fun readText(name: String): String?
    /** Creates or overwrites. */
    fun writeText(name: String, text: String)
    /** Creates [name], which the caller has made unique. */
    fun copyIn(source: File, name: String)
}

class FileNoteFolder(private val directory: File) : NoteFolder {
    override val name: String get() = directory.name
    override fun childNames(): Set<String> = directory.list()?.toSet() ?: emptySet()
    override fun folder(name: String): NoteFolder {
        val child = File(directory, name)
        if (!child.isDirectory && !child.mkdirs()) throw IOException("Cannot create ${child.path}")
        return FileNoteFolder(child)
    }
    override fun readText(name: String): String? = File(directory, name).takeIf { it.isFile }?.readText()
    override fun writeText(name: String, text: String) = AtomicFiles.writeText(File(directory, name), text)
    override fun copyIn(source: File, name: String) {
        source.copyTo(File(directory, name))
    }
}

data class NoteResult(
    /** Relative to the chosen root, e.g. `微信流/张三的聊天.md`. */
    val notePath: String,
    val merged: Boolean,
    val unchanged: Boolean,
)

/**
 * 「沉淀到文件夹」 and 「沉淀到 Obsidian」 share this assembly, as on macOS:
 * `<root>/<subfolder>/聊天名.md` plus `附件/` holding the original ZIPs and the
 * media unpacked from them. A same-named note this app wrote is extended with
 * only the new messages; anything else gets a uniquely numbered sibling.
 */
object KnowledgeDelivery {
    const val DEFAULT_SUBFOLDER = "微信流"
    const val FALLBACK_TITLE = "微信聊天"

    fun deliver(
        root: NoteFolder,
        subfolder: String?,
        material: Material,
        chatName: String?,
        sceneName: String?,
        now: LocalDateTime,
    ): NoteResult {
        if (material.isEmpty) throw IOException("Nothing to write")
        val path = DisplayName.subfolderPath(subfolder).ifEmpty { DEFAULT_SUBFOLDER }
        var folder = root
        for (component in path.split('/')) folder = folder.folder(component)
        val attachmentsFolder = folder.folder(KnowledgeNote.ATTACHMENTS_FOLDER)

        val taken = attachmentsFolder.childNames().toMutableSet()
        fun save(file: File, preferred: String): String {
            val name = DisplayName.unique(DisplayName.sanitize(preferred), taken)
            taken += name
            attachmentsFolder.copyIn(file, name)
            return name
        }
        val archiveNames = material.archives.map { save(it, it.name) }
        val saved = material.attachments.map { it.originalName to save(it.file, it.originalName) }
        val links = LinkedHashMap<String, String>()
        for ((original, name) in saved) links.putIfAbsent(original, name)

        val records = material.records
        val mentioned = records.orEmpty()
            .flatMap { KnowledgeNote.referencedAttachments(it.text, links) }
            .toSet()
        val loose = saved.map { it.second }.filterNot { it in mentioned }

        val title = KnowledgeNote.title(
            chatName,
            records,
            material.archives.firstOrNull()?.let { DisplayName.stem(it.name) } ?: FALLBACK_TITLE,
        )
        val preferred = DisplayName.sanitize(title) + ".md"
        val existing = folder.readText(preferred)
        if (records != null && existing != null) {
            val outcome = KnowledgeNote.merge(
                existingMarkdown = existing,
                records = records,
                attachments = links,
                archiveNames = archiveNames,
                chatName = chatName,
                sceneName = sceneName,
                mergedAt = now,
            )
            when (outcome) {
                is KnowledgeNote.MergeOutcome.Merged -> {
                    folder.writeText(preferred, appendLoose(outcome.markdown, loose))
                    return NoteResult("$path/$preferred", merged = true, unchanged = false)
                }
                KnowledgeNote.MergeOutcome.NothingNew ->
                    return NoteResult("$path/$preferred", merged = false, unchanged = true)
                KnowledgeNote.MergeOutcome.NotApplicable -> Unit
            }
        }

        val markdown = KnowledgeNote.render(
            title = title,
            chatName = chatName,
            sceneName = sceneName,
            createdAt = now,
            records = records,
            body = material.chatText,
            archiveNames = archiveNames,
            attachments = links,
            looseAttachments = loose,
        )
        val noteName = DisplayName.unique(preferred, folder.childNames())
        folder.writeText(noteName, markdown)
        return NoteResult("$path/$noteName", merged = false, unchanged = false)
    }

    private fun appendLoose(markdown: String, loose: List<String>): String {
        if (loose.isEmpty()) return markdown
        return markdown.trimEnd('\n') + "\n\n" + loose.joinToString("\n\n") { KnowledgeNote.attachmentLink(it) } + "\n"
    }
}
