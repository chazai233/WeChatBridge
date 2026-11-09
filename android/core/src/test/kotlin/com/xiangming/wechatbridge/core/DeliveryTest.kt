package com.xiangming.wechatbridge.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DeliveryTest {
    private val root = Fixtures.tempDir()
    private val inbox = Inbox(File(root, "Inbox"))

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun collectionMaterialMergesOverlappingBatches() {
        val first = inbox.commit(listOf(Fixtures.itemFor(Fixtures.exportZip(root))), ShareAction.COLLECT)
        val second = inbox.commit(listOf(IncomingItem.text("b.txt", Fixtures.LATER_TRANSCRIPT)), ShareAction.COLLECT)
        val material = Materializer.materialize(listOf(first, second), File(root, "work"))
        assertEquals(3, material.messageCount)
        assertEquals(listOf("张三", "李四", "王五"), material.participants())
        assertEquals(listOf("photo1.jpg", "clip.mp4"), material.attachments.map { it.originalName })
        assertTrue(material.chatText.startsWith("·张三\n2026年3月5日 09:15\n"))

        val insight = BatchInsights.read(listOf(first, second))
        assertEquals(3, insight.messageCount)
        assertEquals("王五", insight.last?.sender)
    }

    @Test
    fun unreadableZipStillTravels() {
        val fake = File(root, "broken.zip").apply { writeText("nope") }
        val batch = inbox.commit(listOf(Fixtures.itemFor(fake)), ShareAction.CUSTOM)
        val material = Materializer.materialize(listOf(batch), File(root, "work"))
        assertEquals(listOf("broken.zip"), material.unreadable)
        assertEquals(MediaKind.ZIP, material.attachments.single().kind)
        assertNull(material.records)
    }

    @Test
    fun planPutsPromptBeforeChat() {
        val batch = inbox.commit(listOf(IncomingItem.text("a.txt", Fixtures.TRANSCRIPT)), ShareAction.CUSTOM)
        val material = Materializer.materialize(listOf(batch), File(root, "work"))
        val scene = SceneDefaults.library(Lang.ZH).defaultScene!!
        val prompt = ScenePrompt.render(scene, Lang.ZH)!!
        val plan = DeliveryPlanner.plan(material, prompt, includeAttachments = true, lang = Lang.ZH)
        assertTrue(plan.text.startsWith("阅读下面的微信群聊记录"))
        assertTrue(plan.text.contains("输出规范：\n输出以下六项"))
        assertTrue(plan.text.contains("以下是聊天记录：\n·张三"))
        assertEquals(plan.text, plan.clipboardText)
        assertNull(plan.transcriptFileText)
    }

    @Test
    fun longChatTravelsAsAFile() {
        val batch = inbox.commit(listOf(IncomingItem.text("a.txt", Fixtures.TRANSCRIPT)), ShareAction.CUSTOM)
        val material = Materializer.materialize(listOf(batch), File(root, "work"))
        val plan = DeliveryPlanner.plan(material, "总结", includeAttachments = false, lang = Lang.ZH, inlineLimit = 10)
        assertEquals(material.chatText, plan.transcriptFileText)
        assertTrue(plan.text.startsWith("总结\n\n聊天记录较长"))
        assertTrue(plan.attachments.isEmpty())
    }

    @Test
    fun composeWithoutPromptIsJustTheChat() {
        assertEquals("聊天", DeliveryPlanner.compose(null, " 聊天 ", Lang.ZH))
        assertEquals("Summarize\n\nChat transcript:\nhi", DeliveryPlanner.compose("Summarize", "hi", Lang.EN))
    }

    @Test
    fun skillReferencesRenderAsPlainText() {
        val scene = Scene(name = "x", instruction = "用 {{skill:wechat-article-extract}} 提取", outputSpec = "")
        assertEquals("用 「wechat-article-extract」技能（技能文件不可用，请直接完成） 提取", ScenePrompt.render(scene, Lang.ZH))
        assertNull(ScenePrompt.render(Scene(name = "empty", instruction = " "), Lang.ZH))
    }

    @Test
    fun sceneLibraryEdits() {
        val store = SceneStore(File(root, "scenes.json"))
        var library = store.load(Lang.ZH)
        assertEquals(SceneDefaults.GROUP_SUMMARY, library.defaultSceneId)
        assertEquals("群聊总结", library.defaultScene?.name)
        library = library.remove(SceneDefaults.GROUP_SUMMARY)
        assertNull(library.defaultSceneId)
        library = library.upsert(Scene(id = "mine", name = "我的", instruction = "做点什么")).withDefault("mine")
        store.save(library)
        val reloaded = store.load(Lang.EN)
        assertEquals("mine", reloaded.defaultSceneId)
        assertEquals(4, reloaded.scenes.size)
        assertEquals(5, reloaded.restoringStarters(Lang.ZH).scenes.size)
    }

    @Test
    fun targetsAndMimeTypes() {
        assertEquals("com.anthropic.claude", TargetCatalog.byId("claude")?.packageName)
        assertEquals("image/*", MediaKind.commonMimeType(listOf("a.jpg", "b.png")))
        assertEquals("*/*", MediaKind.commonMimeType(listOf("a.jpg", "b.mp4")))
        assertEquals("video/mp4", MediaKind.commonMimeType(listOf("a.mp4")))
    }

    @Test
    fun displayNamesAreSafe() {
        assertEquals("a-b.txt", DisplayName.sanitize("a:b.txt"))
        assertEquals("evil.txt", DisplayName.sanitize("../../evil.txt"))
        assertEquals("共享文件.hidden", DisplayName.sanitize(".hidden"))
        assertEquals("note.md", DisplayName.sanitize("note", "md"))
        assertEquals("参考/微信流", DisplayName.subfolderPath("/参考//微信流/"))
        assertEquals("a 3.md", DisplayName.unique("a.md", listOf("A.md", "a 2.md")))
        val long = "长".repeat(100) + ".jpg"
        val truncated = DisplayName.truncate(long)
        assertTrue(truncated.endsWith(".jpg"))
        assertTrue(truncated.toByteArray().size <= 200)
    }
}
