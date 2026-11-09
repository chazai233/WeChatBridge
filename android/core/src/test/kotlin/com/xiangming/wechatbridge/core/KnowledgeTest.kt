package com.xiangming.wechatbridge.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

class KnowledgeTest {
    private val root = Fixtures.tempDir()
    private val now = LocalDateTime.of(2026, 11, 8, 10, 30)
    private val records = TranscriptParser.parse(Fixtures.TRANSCRIPT)!!

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun titlePrefersChatNameThenParticipants() {
        assertEquals("产品群的聊天", KnowledgeNote.title(" 产品群 ", records, "x"))
        assertEquals("产品群的聊天", KnowledgeNote.title("产品群的聊天", records, "x"))
        assertEquals("张三与李四的聊天", KnowledgeNote.title(null, records, "x"))
        assertEquals("备用", KnowledgeNote.title("", null, "备用"))
    }

    @Test
    fun renderMatchesTheMacOSShape() {
        val markdown = KnowledgeNote.render(
            title = "产品群的聊天",
            chatName = "产品群",
            sceneName = "群聊总结",
            createdAt = now,
            records = records,
            body = null,
            archiveNames = listOf("聊天记录.zip"),
            attachments = mapOf("photo1.jpg" to "photo1.jpg"),
        )
        val expectedHead = listOf(
            "---",
            "title: \"产品群的聊天\"",
            "source: WeChat",
            "chat: \"产品群\"",
            "scene: \"群聊总结\"",
            "exported: 2026-11-08 10:30",
            "messages: 2",
            "archive: \"附件/聊天记录.zip\"",
            "---",
            "",
            "# 产品群的聊天",
            "",
            "> 来源：微信 · 原始归档：[[附件/聊天记录.zip]]",
            "",
            "## 聊天记录",
            "",
            "**张三** · 2026-03-05 09:15",
        ).joinToString("\n")
        assertTrue(markdown, markdown.startsWith(expectedHead))
        assertTrue(markdown.contains("[图片] photo1.jpg\n\n![[附件/photo1.jpg]]"))
    }

    @Test
    fun mergeAddsOnlyNewRecords() {
        val original = KnowledgeNote.render("群的聊天", null, null, now, records, null, emptyList())
        val later = TranscriptParser.parse(Fixtures.LATER_TRANSCRIPT)!!
        val outcome = KnowledgeNote.merge(original, later, emptyMap(), listOf("b.zip"), "群", null, now.plusHours(1))
        val merged = (outcome as KnowledgeNote.MergeOutcome.Merged).markdown
        assertEquals(1, Regex("\\*\\*李四\\*\\*").findAll(merged).count())
        assertTrue(merged.contains("**王五** · 2026-03-05 10:02"))
        assertTrue(merged.contains("messages: 3"))
        assertTrue(merged.contains("chat: \"群\""))
        assertTrue(merged.contains("> 追加归档：[[附件/b.zip]]（2026-11-08 11:30）"))

        val again = KnowledgeNote.merge(merged, later, emptyMap(), emptyList(), null, null, now)
        assertEquals(KnowledgeNote.MergeOutcome.NothingNew, again)
    }

    @Test
    fun userNotesAreNeverMergedInto() {
        val outcome = KnowledgeNote.merge("# 我的笔记\n", records, emptyMap(), emptyList(), null, null, now)
        assertEquals(KnowledgeNote.MergeOutcome.NotApplicable, outcome)
    }

    @Test
    fun deliverWritesNoteAndAttachmentsThenMerges() {
        val inbox = Inbox(File(root, "Inbox"))
        val first = inbox.commit(listOf(Fixtures.itemFor(Fixtures.exportZip(root))), ShareAction.CUSTOM)
        val material = Materializer.materialize(listOf(first), File(root, "work1"))
        val vault = File(root, "vault").apply { mkdirs() }

        val result = KnowledgeDelivery.deliver(FileNoteFolder(vault), "", material, "产品群", "群聊总结", now)
        assertEquals("微信流/产品群的聊天.md", result.notePath)
        val folder = File(vault, "微信流")
        val attachments = File(folder, "附件").list()!!.toSet()
        assertEquals(setOf("聊天记录.zip", "photo1.jpg", "clip.mp4"), attachments)
        val note = File(folder, "产品群的聊天.md").readText()
        assertTrue(note.contains("![[附件/photo1.jpg]]"))
        // The video is not mentioned in any record, so it is listed at the end.
        assertTrue(note.contains("## 附件\n\n![[附件/clip.mp4]]"))

        val laterZip = Fixtures.exportZip(File(root, "later").apply { mkdirs() }, transcript = Fixtures.LATER_TRANSCRIPT)
        val second = inbox.commit(listOf(Fixtures.itemFor(laterZip)), ShareAction.CUSTOM)
        val more = Materializer.materialize(listOf(second), File(root, "work2"))
        val merged = KnowledgeDelivery.deliver(FileNoteFolder(vault), null, more, "产品群", null, now.plusHours(1))
        assertTrue(merged.merged)
        assertEquals(listOf("产品群的聊天.md", "附件"), folder.list()!!.sorted())
        val text = File(folder, "产品群的聊天.md").readText()
        assertTrue(text.contains("**王五**"))
        assertTrue(text.contains("聊天记录 2.zip"))
    }

    @Test
    fun pastedTextBecomesAPlainNote() {
        val inbox = Inbox(File(root, "Inbox"))
        val batch = inbox.commit(listOf(IncomingItem.text("粘贴.txt", "张三：明天见\n李四：好的")), ShareAction.CUSTOM)
        val material = Materializer.materialize(listOf(batch), File(root, "work"))
        val result = KnowledgeDelivery.deliver(FileNoteFolder(root), "笔记/微信", material, null, null, now)
        assertEquals("笔记/微信/微信聊天.md", result.notePath)
        val note = File(root, "笔记/微信/微信聊天.md").readText()
        assertTrue(note.contains("## 聊天记录\n\n张三：明天见\n李四：好的"))
        assertFalse(note.contains("messages:"))
    }
}
