package com.xiangming.wechatbridge.core

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.Normalizer
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

class ArchiveException(message: String, cause: Throwable? = null) : IOException(message, cause)

data class ArchiveContents(val transcript: Transcript?, val fileNames: List<String>)

data class ExtractedFile(val entryName: String, val file: File) {
    val leafName: String get() = entryName.substringAfterLast('/')
}

/**
 * Reads a WeChat export ZIP: the transcript TXT becomes chat text, everything
 * else is an attachment. Port of the macOS `WeChatNativeArchive` rules —
 * entry count, sizes and names are validated before anything is read, and
 * extraction flattens into one private folder so no ZIP path ever reaches the
 * filesystem.
 */
object WeChatArchive {
    const val MAX_ENTRIES = 1000
    const val MAX_TOTAL_BYTES = 1L shl 30
    const val MAX_TEXT_BYTES = 16L * 1024 * 1024
    const val NATIVE_TRANSCRIPT = "聊天记录.txt"

    /** The transcript, preferring WeChat's own `聊天记录.txt`, then the TXT with the most records. */
    fun read(zip: File): ArchiveContents = open(zip) { archive, entries ->
        val candidates = mutableListOf<Transcript>()
        for (entry in entries) {
            if (entry.isDirectory || !entry.name.lowercase().endsWith(".txt")) continue
            if (entry.size > MAX_TEXT_BYTES) continue
            val bytes = archive.getInputStream(entry).use { readBounded(it, MAX_TEXT_BYTES) } ?: continue
            val text = TextDecoding.strictUtf8(bytes) ?: continue
            candidates += Transcript(entry.name, text, TranscriptParser.parse(text))
        }
        val native = candidates.firstOrNull { it.path.substringAfterLast('/') == NATIVE_TRANSCRIPT }
        ArchiveContents(
            transcript = native ?: candidates.maxByOrNull { it.records?.size ?: 0 },
            fileNames = entries.filter { !it.isDirectory }.map { it.name },
        )
    }

    /**
     * Unpacks every file except [skip] into [destination], flattened to unique,
     * sanitised leaf names. The byte budget is enforced while streaming, so an
     * entry that lies about its size cannot fill the disk.
     */
    fun extract(zip: File, destination: File, skip: Set<String> = emptySet()): List<ExtractedFile> =
        open(zip) { archive, entries ->
            if (!destination.isDirectory && !destination.mkdirs()) {
                throw ArchiveException("Cannot create ${destination.path}")
            }
            val taken = destination.list()?.toMutableSet() ?: mutableSetOf()
            var written = 0L
            val result = mutableListOf<ExtractedFile>()
            for (entry in entries) {
                if (entry.isDirectory || entry.name in skip) continue
                val name = DisplayName.unique(DisplayName.sanitize(entry.name.substringAfterLast('/')), taken)
                taken += name
                val output = File(destination, name)
                archive.getInputStream(entry).use { input ->
                    FileOutputStream(output).use { sink ->
                        written += copyBounded(input, sink, MAX_TOTAL_BYTES - written)
                    }
                }
                result += ExtractedFile(entry.name, output)
            }
            result
        }

    private fun <T> open(zip: File, body: (ZipFile, List<ZipEntry>) -> T): T {
        val archive = try {
            ZipFile(zip, Charsets.UTF_8)
        } catch (error: IOException) {
            throw ArchiveException("Not a readable ZIP: ${zip.name}", error)
        } catch (error: IllegalArgumentException) {
            // A name that is not valid UTF-8 surfaces as an IllegalArgumentException.
            throw ArchiveException("Not a readable ZIP: ${zip.name}", error)
        }
        return archive.use {
            val entries = try {
                it.entries().toList()
            } catch (error: IllegalArgumentException) {
                throw ArchiveException("Unreadable entry name in ${zip.name}", error)
            }
            validate(entries)
            try {
                body(it, entries)
            } catch (error: ArchiveException) {
                throw error
            } catch (error: IOException) {
                throw ArchiveException("Corrupt ZIP: ${zip.name}", error)
            }
        }
    }

    private fun validate(entries: List<ZipEntry>) {
        if (entries.isEmpty() || entries.size > MAX_ENTRIES) {
            throw ArchiveException("Unexpected entry count: ${entries.size}")
        }
        var total = 0L
        val seen = HashSet<String>()
        for (entry in entries) {
            val path = entry.name.removeSuffix("/")
            val parts = path.split('/')
            val unsafe = path.isEmpty() ||
                parts.any { it.isEmpty() || it == "." || it == ".." } ||
                '\\' in path || ':' in path ||
                path.any { Character.isISOControl(it) }
            if (unsafe) throw ArchiveException("Unsafe entry name: ${entry.name}")
            if (entry.size > 0) total += entry.size
            if (total > MAX_TOTAL_BYTES) throw ArchiveException("Archive expands beyond the limit")
            // Case and Unicode-normalisation aliases would overwrite each other on disk.
            val key = Normalizer.normalize(entry.name, Normalizer.Form.NFC).lowercase()
            if (!seen.add(key)) throw ArchiveException("Duplicate entry: ${entry.name}")
        }
    }

    private fun readBounded(input: InputStream, limit: Long): ByteArray? {
        val bytes = input.readNBytesCompat(limit + 1)
        return if (bytes.size > limit) null else bytes
    }

    private fun InputStream.readNBytesCompat(limit: Long): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var remaining = limit
        while (remaining > 0) {
            val count = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count < 0) break
            output.write(buffer, 0, count)
            remaining -= count
        }
        return output.toByteArray()
    }

    internal fun copyBounded(input: InputStream, output: OutputStream, budget: Long): Long {
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            copied += count
            if (copied > budget) throw ArchiveException("Archive expands beyond the limit")
            output.write(buffer, 0, count)
        }
        return copied
    }
}
