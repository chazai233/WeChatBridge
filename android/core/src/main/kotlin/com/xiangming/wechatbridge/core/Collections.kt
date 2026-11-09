package com.xiangming.wechatbridge.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID

@Serializable
enum class CollectionStatus {
    @SerialName("collecting") COLLECTING,
    @SerialName("draft") DRAFT,
    @SerialName("delivering") DELIVERING,
    @SerialName("delivered") DELIVERED,
    @SerialName("retry") RETRY,
}

@Serializable
data class BatchCollection(
    val id: String,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    val name: String = "",
    @SerialName("batchIDs") val batchIds: List<String> = emptyList(),
    val status: CollectionStatus = CollectionStatus.COLLECTING,
    val targetName: String? = null,
    val detail: String? = null,
    val sceneName: String? = null,
    val defaultChatName: String? = null,
)

class CollectionException(val reason: Reason) : IOException(reason.name) {
    enum class Reason { MISSING, BUSY, EMPTY, INVALID }
}

/**
 * 「分批收集」, ported from macOS `BatchCollectionLedger`. Only the app writes
 * it; the batches themselves stay in `Ready/` with no duplicated payload.
 * Seen ids survive detaching a member, so a reload cannot collect it twice.
 * Every mutation returns a new ledger; the caller saves it.
 */
@Serializable
data class CollectionLedger(
    val schemaVersion: Int = 1,
    val collections: List<BatchCollection> = emptyList(),
    @SerialName("seenBatchIDs") val seenBatchIds: Set<String> = emptySet(),
) {
    val current: BatchCollection? get() = collections.firstOrNull { it.status == CollectionStatus.COLLECTING }

    /** Members of unfinished collections, which history clean-up must leave alone. */
    val protectedBatchIds: Set<String>
        get() = collections.filter { it.status != CollectionStatus.DELIVERED }.flatMap { it.batchIds }.toSet()

    fun collection(id: String): BatchCollection? = collections.firstOrNull { it.id == id }

    fun collectionOf(batchId: String): BatchCollection? = collections.firstOrNull { batchId in it.batchIds }

    /** Adds to the open collection, opening one if none is. Returns the collection id. */
    fun append(
        batchId: String,
        at: Instant = Instant.now(),
        newId: () -> String = { UUID.randomUUID().toString().uppercase() },
    ): Pair<CollectionLedger, String> {
        if (batchId in seenBatchIds) return this to (collectionOf(batchId)?.id ?: batchId)
        val open = current ?: BatchCollection(id = newId(), createdAt = at)
        val updated = open.copy(batchIds = open.batchIds + batchId)
        val next = if (current == null) collections + updated else collections.map { if (it.id == open.id) updated else it }
        return copy(collections = next, seenBatchIds = seenBatchIds + batchId) to updated.id
    }

    fun resume(id: String): CollectionLedger {
        val target = editable(id)
        if (target.batchIds.isEmpty()) throw CollectionException(CollectionException.Reason.EMPTY)
        return copy(
            collections = collections.map {
                when {
                    it.id == id -> it.copy(status = CollectionStatus.COLLECTING, detail = null)
                    it.status == CollectionStatus.COLLECTING -> it.copy(status = CollectionStatus.DRAFT)
                    else -> it
                }
            },
        )
    }

    fun parkCurrent(): CollectionLedger = copy(
        collections = collections.map {
            if (it.status == CollectionStatus.COLLECTING) it.copy(status = CollectionStatus.DRAFT) else it
        },
    )

    fun rename(id: String, name: String): CollectionLedger {
        editable(id)
        return replace(id) { it.copy(name = name.trim().take(120)) }
    }

    fun setDefaultChatName(id: String, name: String): CollectionLedger {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw CollectionException(CollectionException.Reason.INVALID)
        editable(id)
        return replace(id) { it.copy(defaultChatName = trimmed) }
    }

    /** Detaches a batch from whichever collection holds it, e.g. when the batch is deleted. */
    fun detach(batchId: String): CollectionLedger = copy(
        collections = collections.map { collection ->
            if (batchId !in collection.batchIds) return@map collection
            val remaining = collection.batchIds - batchId
            collection.copy(
                batchIds = remaining,
                status = if (remaining.isEmpty() && collection.status == CollectionStatus.COLLECTING) {
                    CollectionStatus.DRAFT
                } else {
                    collection.status
                },
            )
        },
    )

    /** Freezes the collection before anything is sent; later shares open a new one. */
    fun beginDelivery(id: String): Pair<CollectionLedger, List<String>> {
        val target = editable(id)
        if (target.batchIds.isEmpty()) throw CollectionException(CollectionException.Reason.EMPTY)
        val next = replace(id) { it.copy(status = CollectionStatus.DELIVERING, detail = null) }
        return next to target.batchIds
    }

    fun finishDelivery(id: String, succeeded: Boolean, target: String, detail: String, scene: String?): CollectionLedger {
        val collection = collection(id) ?: throw CollectionException(CollectionException.Reason.MISSING)
        if (collection.status != CollectionStatus.DELIVERING) throw CollectionException(CollectionException.Reason.MISSING)
        return replace(id) {
            it.copy(
                status = if (succeeded) CollectionStatus.DELIVERED else CollectionStatus.RETRY,
                targetName = target,
                detail = detail,
                sceneName = scene,
            )
        }
    }

    /** A delivery the app died during: keep the files, ask the user to retry. */
    fun recoverInterruptedDeliveries(detail: String): CollectionLedger = copy(
        collections = collections.map {
            if (it.status == CollectionStatus.DELIVERING) it.copy(status = CollectionStatus.RETRY, detail = detail) else it
        },
    )

    fun forget(id: String): CollectionLedger {
        editable(id)
        return copy(collections = collections.filterNot { it.id == id })
    }

    /** The checks macOS runs on load: unique ids, each batch in one collection, at most one open. */
    fun validate(): CollectionLedger {
        val members = collections.flatMap { it.batchIds }
        val valid = schemaVersion == 1 &&
            collections.map { it.id }.toSet().size == collections.size &&
            members.toSet().size == members.size &&
            seenBatchIds.containsAll(members) &&
            collections.count { it.status == CollectionStatus.COLLECTING } <= 1
        if (!valid) throw CollectionException(CollectionException.Reason.INVALID)
        return this
    }

    private fun editable(id: String): BatchCollection {
        val collection = collection(id) ?: throw CollectionException(CollectionException.Reason.MISSING)
        if (collection.status == CollectionStatus.DELIVERING) throw CollectionException(CollectionException.Reason.BUSY)
        return collection
    }

    private fun replace(id: String, transform: (BatchCollection) -> BatchCollection): CollectionLedger =
        copy(collections = collections.map { if (it.id == id) transform(it) else it })
}

/** `Inbox/collections.json`. An unreadable ledger is kept aside so the batches stay recoverable. */
class CollectionStore(private val file: File) {
    fun load(): CollectionLedger {
        if (!file.isFile) return CollectionLedger()
        return runCatching { BridgeJson.decodeFromString<CollectionLedger>(file.readText()).validate() }.getOrElse {
            file.renameTo(File(file.parentFile, "${file.name}.unreadable-${System.currentTimeMillis()}"))
            CollectionLedger()
        }
    }

    fun save(ledger: CollectionLedger) {
        AtomicFiles.writeText(file, BridgeJson.encodeToString(ledger))
    }
}
