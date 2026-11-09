package com.xiangming.wechatbridge.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WeChatArchiveTest {
    private val root = Fixtures.tempDir()

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun readsTheNativeTranscript() {
        val zip = Fixtures.exportZip(root)
        val contents = WeChatArchive.read(zip)
        val transcript = contents.transcript!!
        assertEquals("聊天记录/聊天记录.txt", transcript.path)
        assertEquals(2, transcript.records?.size)
        assertEquals(3, contents.fileNames.size)
    }

    @Test
    fun prefersNativeNameOverALargerTxt() {
        val zip = Fixtures.zip(
            File(root, "a.zip"),
            linkedMapOf(
                "notes.txt" to (Fixtures.TRANSCRIPT + Fixtures.LATER_TRANSCRIPT).toByteArray(),
                "聊天记录.txt" to Fixtures.LATER_TRANSCRIPT.toByteArray(),
            ),
        )
        assertEquals("聊天记录.txt", WeChatArchive.read(zip).transcript?.path)
    }

    @Test
    fun extractsMediaFlattenedAndSkipsTheTranscript() {
        val zip = Fixtures.exportZip(root)
        val destination = File(root, "out")
        val files = WeChatArchive.extract(zip, destination, setOf("聊天记录/聊天记录.txt"))
        assertEquals(listOf("photo1.jpg", "clip.mp4"), files.map { it.file.name })
        assertTrue(files.all { it.file.parentFile == destination })
    }

    @Test
    fun duplicateLeafNamesAreNumbered() {
        val zip = Fixtures.zip(
            File(root, "dup.zip"),
            linkedMapOf("a/x.jpg" to byteArrayOf(1), "b/x.jpg" to byteArrayOf(2)),
        )
        val names = WeChatArchive.extract(zip, File(root, "out")).map { it.file.name }
        assertEquals(listOf("x.jpg", "x 2.jpg"), names)
    }

    @Test(expected = ArchiveException::class)
    fun rejectsPathTraversal() {
        val zip = Fixtures.zip(File(root, "evil.zip"), mapOf("../evil.txt" to byteArrayOf(1)))
        WeChatArchive.read(zip)
    }

    @Test(expected = ArchiveException::class)
    fun rejectsSomethingThatIsNotAZip() {
        val file = File(root, "fake.zip").apply { writeText("not a zip") }
        WeChatArchive.read(file)
    }

    @Test
    fun archiveWithoutTxtHasNoTranscript() {
        val zip = Fixtures.zip(File(root, "media.zip"), mapOf("a.jpg" to byteArrayOf(1)))
        val contents = WeChatArchive.read(zip)
        assertEquals(null, contents.transcript)
        assertFalse(contents.fileNames.isEmpty())
    }
}
