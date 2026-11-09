package com.xiangming.wechatbridge.core

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Fixtures {
    /** Two messages in WeChat's export layout, with a BOM and CRLF endings as WeChat writes them. */
    const val TRANSCRIPT =
        "\uFEFF\u00B7张三\r\n2026年3月5日 09:15\r\n大家早，今天下午三点开会。\r\n\r\n" +
            "\u00B7李四\r\n2026年3月5日 09:17\r\n[图片] photo1.jpg\r\n"

    const val LATER_TRANSCRIPT =
        "\u00B7李四\n2026年3月5日 09:17\n[图片] photo1.jpg\n\n" +
            "\u00B7王五\n2026年3月5日 10:02\n收到，我准备材料。\n"

    fun tempDir(prefix: String = "wb-test"): File = Files.createTempDirectory(prefix).toFile()

    fun zip(file: File, entries: Map<String, ByteArray>): File {
        ZipOutputStream(FileOutputStream(file)).use { out ->
            for ((name, bytes) in entries) {
                out.putNextEntry(ZipEntry(name))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return file
    }

    fun exportZip(directory: File, name: String = "聊天记录.zip", transcript: String = TRANSCRIPT): File =
        zip(
            File(directory, name),
            linkedMapOf(
                "聊天记录/聊天记录.txt" to transcript.toByteArray(Charsets.UTF_8),
                "聊天记录/photo1.jpg" to byteArrayOf(1, 2, 3, 4),
                "聊天记录/clip.mp4" to byteArrayOf(5, 6, 7),
            ),
        )

    fun itemFor(file: File, contentType: String? = null): IncomingItem =
        IncomingItem(file.name, contentType) { file.inputStream() }
}
