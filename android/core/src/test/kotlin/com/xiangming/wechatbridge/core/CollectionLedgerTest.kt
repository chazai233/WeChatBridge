package com.xiangming.wechatbridge.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.time.Instant

class CollectionLedgerTest {
    private val now = Instant.parse("2026-11-08T09:00:00Z")
    private var counter = 0
    private val ids = { "C${++counter}" }

    @Test
    fun appendsToOneOpenCollection() {
        var ledger = CollectionLedger()
        val (first, firstId) = ledger.append("B1", now, ids)
        ledger = first
        val (second, secondId) = ledger.append("B2", now, ids)
        ledger = second
        assertEquals(firstId, secondId)
        assertEquals(listOf("B1", "B2"), ledger.current?.batchIds)
        assertEquals(setOf("B1", "B2"), ledger.protectedBatchIds)
    }

    @Test
    fun aSeenBatchIsNotCollectedAgain() {
        val (ledger, id) = CollectionLedger().append("B1", now, ids)
        val (again, sameId) = ledger.append("B1", now, ids)
        assertEquals(ledger, again)
        assertEquals(id, sameId)
    }

    @Test
    fun parkThenResume() {
        var (ledger, id) = CollectionLedger().append("B1", now, ids)
        ledger = ledger.parkCurrent()
        assertNull(ledger.current)
        val (withNew, newId) = ledger.append("B2", now, ids)
        assertTrue(newId != id)
        ledger = withNew.resume(id)
        assertEquals(id, ledger.current?.id)
        assertEquals(CollectionStatus.DRAFT, ledger.collection(newId)?.status)
    }

    @Test
    fun deliveryFreezesAndFinishes() {
        var (ledger, id) = CollectionLedger().append("B1", now, ids)
        val (delivering, members) = ledger.beginDelivery(id)
        ledger = delivering
        assertEquals(listOf("B1"), members)
        assertNull(ledger.current)
        try {
            ledger.forget(id)
            fail("a delivering collection is not editable")
        } catch (error: CollectionException) {
            assertEquals(CollectionException.Reason.BUSY, error.reason)
        }
        // A share arriving now opens a new collection instead of joining the frozen one.
        val (withNew, newId) = ledger.append("B2", now, ids)
        assertTrue(newId != id)
        ledger = withNew.finishDelivery(id, succeeded = true, target = "Claude", detail = "ok", scene = "群聊总结")
        assertEquals(CollectionStatus.DELIVERED, ledger.collection(id)?.status)
        assertEquals(setOf("B2"), ledger.protectedBatchIds)
    }

    @Test
    fun interruptedDeliveryBecomesRetry() {
        val (ledger, id) = CollectionLedger().append("B1", now, ids)
        val recovered = ledger.beginDelivery(id).first.recoverInterruptedDeliveries("interrupted")
        assertEquals(CollectionStatus.RETRY, recovered.collection(id)?.status)
        assertEquals("interrupted", recovered.collection(id)?.detail)
    }

    @Test
    fun detachKeepsTheBatchSeen() {
        val (ledger, id) = CollectionLedger().append("B1", now, ids)
        val detached = ledger.detach("B1")
        assertEquals(emptyList<String>(), detached.collection(id)?.batchIds)
        assertEquals(CollectionStatus.DRAFT, detached.collection(id)?.status)
        assertTrue("B1" in detached.seenBatchIds)
    }

    @Test
    fun emptyCollectionCannotBeDelivered() {
        val (ledger, id) = CollectionLedger().append("B1", now, ids)
        try {
            ledger.detach("B1").beginDelivery(id)
            fail("expected EMPTY")
        } catch (error: CollectionException) {
            assertEquals(CollectionException.Reason.EMPTY, error.reason)
        }
    }

    @Test
    fun storeRoundTripsAndSetsAsideAnInvalidLedger() {
        val root = Fixtures.tempDir()
        try {
            val file = File(root, "collections.json")
            val store = CollectionStore(file)
            val (ledger, _) = CollectionLedger().append("B1", now, ids)
            store.save(ledger)
            assertEquals(ledger, store.load())
            assertTrue(file.readText().contains("\"batchIDs\""))

            file.writeText("""{"schemaVersion":1,"collections":[{"id":"C","createdAt":"2026-11-08T09:00:00Z","batchIDs":["X"]}],"seenBatchIDs":[]}""")
            assertEquals(CollectionLedger(), store.load())
            assertTrue(root.list()!!.any { it.startsWith("collections.json.unreadable-") })
        } finally {
            root.deleteRecursively()
        }
    }

    @After
    fun reset() {
        counter = 0
    }
}
