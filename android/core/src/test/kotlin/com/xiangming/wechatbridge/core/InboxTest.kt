package com.xiangming.wechatbridge.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.time.Instant

class InboxTest {
    private val root = Fixtures.tempDir()
    private val now = Instant.parse("2026-11-08T09:04:42.123Z")
    private val inbox = Inbox(File(root, "Inbox"), clock = { now })

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun commitPublishesACompleteBatch() {
        val zip = Fixtures.exportZip(root)
        val batch = inbox.commit(
            listOf(Fixtures.itemFor(zip, "application/zip"), IncomingItem.text("粘贴的文本.txt", "你好")),
            ShareAction.CUSTOM,
            BatchState(action = ShareAction.CUSTOM),
        )
        assertEquals(File(inbox.ready, batch.id), batch.directory)
        assertEquals(listOf("聊天记录.zip", "粘贴的文本.txt"), batch.manifest.items.map { it.displayName })
        assertEquals("files/聊天记录.zip", batch.manifest.items[0].relativePath)
        assertTrue(batch.files.all { it.isFile })
        assertEquals(0, inbox.staging.listFiles()!!.size)

        val manifest = File(batch.directory, BatchManifest.FILE_NAME).readText()
        assertTrue(manifest.contains("\"schemaVersion\": 1"))
        assertTrue(manifest.contains("\"batchID\": \"${batch.id}\""))
        assertTrue(manifest.contains("\"createdAt\": \"2026-11-08T09:04:42Z\""))
        assertTrue(manifest.contains("\"action\": \"custom\""))

        val reread = inbox.batch(batch.id)!!
        // Stored to the whole second, as the shared contract writes it.
        assertEquals(Instant.parse("2026-11-08T09:04:42Z"), reread.manifest.createdAt)
        assertEquals(batch.manifest.copy(createdAt = reread.manifest.createdAt), reread.manifest)
        assertEquals(ShareAction.CUSTOM, reread.state?.action)
    }

    @Test
    fun sameNamesInOneShareAreNumbered() {
        val batch = inbox.commit(
            listOf(IncomingItem.text("a.txt", "1"), IncomingItem.text("a.txt", "2")),
            ShareAction.COLLECT,
        )
        assertEquals(listOf("a.txt", "a 2.txt"), batch.manifest.items.map { it.displayName })
    }

    @Test
    fun failedImportLeavesNothingInReady() {
        val broken = IncomingItem("x.jpg", "image/jpeg") {
            object : InputStream() {
                override fun read(): Int = throw java.io.IOException("stream closed by host")
            }
        }
        try {
            inbox.commit(listOf(broken), ShareAction.CUSTOM)
            fail("expected an InboxException")
        } catch (ignored: InboxException) {
        }
        assertEquals(0, inbox.ready.listFiles()!!.size)
        assertEquals(0, inbox.staging.listFiles()!!.size)
        assertEquals(1, inbox.failed.listFiles()!!.size)
    }

    @Test
    fun oversizedShareIsRejected() {
        val small = Inbox(File(root, "Small"), maxFileBytes = 4, maxBatchBytes = 4)
        try {
            small.commit(listOf(IncomingItem.text("a.txt", "too long")), ShareAction.CUSTOM)
            fail("expected TOO_LARGE")
        } catch (error: InboxException) {
            assertEquals(InboxException.Reason.TOO_LARGE, error.reason)
        }
    }

    @Test
    fun stateIsWrittenAndUpdated() {
        val batch = inbox.commit(listOf(IncomingItem.text("a.txt", "1")), ShareAction.CUSTOM)
        assertNull(batch.state)
        inbox.updateState(batch.id) {
            it.copy(outcome = BatchOutcome(OutcomeKind.COPIED, "剪贴板", now), targetName = "剪贴板")
        }
        val state = inbox.batch(batch.id)!!.state!!
        assertEquals(OutcomeKind.COPIED, state.outcome?.kind)
        assertEquals("剪贴板", state.targetName)
        val json = File(batch.directory, BatchState.FILE_NAME).readText()
        assertTrue(json.contains("\"kind\": \"copied\""))
    }

    @Test
    fun deleteRemovesTheBatchAndRejectsUnsafeIds() {
        val batch = inbox.commit(listOf(IncomingItem.text("a.txt", "1")), ShareAction.CUSTOM)
        assertFalse(inbox.delete("../Ready"))
        assertTrue(inbox.delete(batch.id))
        assertNull(inbox.batch(batch.id))
        assertTrue(inbox.batches().isEmpty())
    }

    @Test
    fun pruneRemovesOnlyOldStaging() {
        inbox.ensureCreated()
        val old = File(inbox.staging, "old.staging").apply { mkdirs(); setLastModified(0) }
        val fresh = File(inbox.staging, "fresh.staging").apply { mkdirs() }
        assertEquals(1, inbox.pruneStaging())
        assertFalse(old.exists())
        assertTrue(fresh.exists())
    }
}
