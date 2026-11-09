package com.xiangming.wechatbridge.deliver

import android.content.ContentResolver
import androidx.documentfile.provider.DocumentFile
import com.xiangming.wechatbridge.core.NoteFolder
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * [NoteFolder] over a SAF tree the user picked. Files are created as
 * `application/octet-stream` so providers keep the name exactly as given
 * instead of appending an extension guessed from a MIME type.
 */
class DocumentNoteFolder(
    private val resolver: ContentResolver,
    private val directory: DocumentFile,
) : NoteFolder {
    override val name: String get() = directory.name.orEmpty()

    override fun childNames(): Set<String> = directory.listFiles().mapNotNull { it.name }.toSet()

    override fun folder(name: String): NoteFolder {
        val existing = directory.findFile(name)?.takeIf { it.isDirectory }
        val child = existing ?: directory.createDirectory(name) ?: throw IOException("Cannot create folder $name")
        return DocumentNoteFolder(resolver, child)
    }

    override fun readText(name: String): String? {
        val file = directory.findFile(name)?.takeIf { it.isFile } ?: return null
        return resolver.openInputStream(file.uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    override fun writeText(name: String, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val existing = directory.findFile(name)?.takeIf { it.isFile }
        val file = existing ?: create(name)
        // "wt" truncates; a provider that does not support it gets a fresh file instead.
        val stream = try {
            resolver.openOutputStream(file.uri, "wt")
        } catch (ignored: FileNotFoundException) {
            null
        } catch (ignored: IllegalArgumentException) {
            null
        } catch (ignored: UnsupportedOperationException) {
            null
        }
        if (stream != null) {
            stream.use { it.write(bytes) }
            return
        }
        if (existing != null) existing.delete()
        val fresh = if (existing != null) create(name) else file
        resolver.openOutputStream(fresh.uri)?.use { it.write(bytes) } ?: throw IOException("Cannot write $name")
    }

    override fun copyIn(source: File, name: String) {
        val file = create(name)
        val output = resolver.openOutputStream(file.uri) ?: throw IOException("Cannot write $name")
        output.use { sink -> source.inputStream().use { it.copyTo(sink) } }
    }

    private fun create(name: String): DocumentFile =
        directory.createFile("application/octet-stream", name) ?: throw IOException("Cannot create $name")
}
