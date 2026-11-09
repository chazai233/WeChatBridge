package com.xiangming.wechatbridge.data

import android.content.Context
import android.content.pm.PackageManager
import com.xiangming.wechatbridge.R
import com.xiangming.wechatbridge.core.BatchCollection
import com.xiangming.wechatbridge.core.BatchInsight
import com.xiangming.wechatbridge.core.BatchInsights
import com.xiangming.wechatbridge.core.BatchOutcome
import com.xiangming.wechatbridge.core.BatchState
import com.xiangming.wechatbridge.core.CollectionLedger
import com.xiangming.wechatbridge.core.CollectionStatus
import com.xiangming.wechatbridge.core.CollectionStore
import com.xiangming.wechatbridge.core.DeliveryTarget
import com.xiangming.wechatbridge.core.Inbox
import com.xiangming.wechatbridge.core.IncomingItem
import com.xiangming.wechatbridge.core.Lang
import com.xiangming.wechatbridge.core.Material
import com.xiangming.wechatbridge.core.Materializer
import com.xiangming.wechatbridge.core.OutcomeKind
import com.xiangming.wechatbridge.core.ReadyBatch
import com.xiangming.wechatbridge.core.Scene
import com.xiangming.wechatbridge.core.SceneLibrary
import com.xiangming.wechatbridge.core.SceneStore
import com.xiangming.wechatbridge.core.ShareAction
import com.xiangming.wechatbridge.core.TargetCatalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.time.Instant
import java.util.UUID

/** What a list row needs, without unpacking anything. */
data class BatchView(
    val id: String,
    val createdAt: Instant,
    val title: String,
    val itemCount: Int,
    val state: BatchState?,
    val collectionId: String?,
) {
    val outcome: BatchOutcome? get() = state?.outcome
}

data class CollectionView(val collection: BatchCollection, val insight: BatchInsight?)

data class HomeState(val collecting: CollectionView?, val pending: List<BatchView>)

data class HistoryState(val collections: List<CollectionView>, val batches: List<BatchView>)

data class Imported(val batchId: String, val collectionId: String?, val collectedCount: Int)

/** Who is being sent: loose batches, or every member of a collection. */
sealed interface SendSubject {
    data class Batches(val ids: List<String>) : SendSubject
    data class Collection(val id: String) : SendSubject
}

class PreparedSend(
    val batchIds: List<String>,
    val collectionId: String?,
    val material: Material,
    val scenes: SceneLibrary,
    val suggestedChatName: String,
    val installedPackages: Set<String>,
)

/**
 * The app's one gateway to its storage: Inbox batches, the collection ledger,
 * scenes and preferences. Every mutation bumps [revision] so screens reload.
 * Call from a background dispatcher; the methods do file I/O.
 */
class Bridge(private val baseContext: Context) {
    val preferences = Preferences(baseContext)
    private val context: Context get() = baseContext.inAppLanguage(lang())
    val inbox = Inbox(File(baseContext.filesDir, "Inbox"))
    private val sceneStore = SceneStore(File(context.filesDir, "scenes.json"))
    private val collectionStore = CollectionStore(File(inbox.root, "collections.json"))
    private val workRoot = File(context.cacheDir, "work")
    private val lock = Any()

    private val changes = MutableStateFlow(0)
    val revision: StateFlow<Int> = changes

    fun lang(): Lang = preferences.language

    fun housekeeping() {
        inbox.ensureCreated()
        inbox.pruneStaging()
        // Unpacked media from earlier deliveries. A day is long enough for any
        // receiving app to have read what it was handed.
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        workRoot.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
        updateLedger { it.recoverInterruptedDeliveries(context.getString(R.string.collection_interrupted)) }
    }

    // region Import

    fun importShare(items: List<IncomingItem>, collect: Boolean): Imported {
        val action = if (collect) ShareAction.COLLECT else ShareAction.CUSTOM
        val batch = inbox.commit(items, action, BatchState(action = action))
        inbox.log("import ${batch.id} action=${action.name} items=${items.size}")
        if (!collect) {
            changed()
            return Imported(batch.id, null, 0)
        }
        var collectionId = ""
        val ledger = updateLedger { ledger ->
            val (next, id) = ledger.append(batch.id)
            collectionId = id
            next
        }
        return Imported(batch.id, collectionId, ledger.collection(collectionId)?.batchIds?.size ?: 1)
    }

    // endregion

    // region Queries

    fun ledger(): CollectionLedger = synchronized(lock) { collectionStore.load() }

    fun homeState(): HomeState {
        val ledger = ledger()
        val batches = inbox.batches()
        val collecting = ledger.current?.let { CollectionView(it, insight(it)) }
        val pending = batches
            .filter { it.state?.outcome == null && ledger.collectionOf(it.id) == null }
            .map { view(it, ledger) }
        return HomeState(collecting, pending)
    }

    fun historyState(): HistoryState {
        val ledger = ledger()
        val collections = ledger.collections
            .filter { it.batchIds.isNotEmpty() }
            .sortedByDescending { it.createdAt }
            .map { CollectionView(it, null) }
        return HistoryState(collections, inbox.batches().map { view(it, ledger) })
    }

    fun insight(collection: BatchCollection): BatchInsight =
        BatchInsights.read(collection.batchIds.mapNotNull { inbox.batch(it) })

    fun sceneLibrary(): SceneLibrary = synchronized(lock) { loadScenes() }

    private fun loadScenes(): SceneLibrary {
        val stored = sceneStore.load(lang())
        return stored.localizingUneditedStarters(lang()).also { if (it != stored) sceneStore.save(it) }
    }

    fun updateScenes(transform: (SceneLibrary) -> SceneLibrary): SceneLibrary {
        val next = synchronized(lock) {
            transform(loadScenes()).also { sceneStore.save(it) }
        }
        changed()
        return next
    }

    fun prepare(subject: SendSubject): PreparedSend {
        val (ids, collection) = when (subject) {
            is SendSubject.Batches -> subject.ids to null
            is SendSubject.Collection -> {
                val collection = ledger().collection(subject.id)
                    ?: throw IllegalStateException(context.getString(R.string.collection_missing))
                collection.batchIds to collection
            }
        }
        val batches = ids.mapNotNull { inbox.batch(it) }
        if (batches.isEmpty()) throw IllegalStateException(context.getString(R.string.send_nothing))
        val material = materialize(batches)
        val savedName = collection?.defaultChatName ?: batches.firstNotNullOfOrNull { it.state?.chatName }
        return PreparedSend(
            batchIds = batches.map { it.id },
            collectionId = collection?.id,
            material = material,
            scenes = sceneLibrary(),
            suggestedChatName = savedName.orEmpty(),
            installedPackages = installedPackages(),
        )
    }

    fun materialize(batches: List<ReadyBatch>): Material {
        val directory = File(workRoot, UUID.randomUUID().toString())
        return Materializer.materialize(batches, directory)
    }

    /** The chat text alone, for 「复制」 in 记录. */
    fun chatText(batchIds: List<String>): String =
        materialize(batchIds.mapNotNull { inbox.batch(it) }).chatText

    fun installedPackages(): Set<String> {
        val manager = context.packageManager
        return TargetCatalog.all.mapNotNull { it.packageName }.filter { name ->
            try {
                @Suppress("DEPRECATION")
                manager.getPackageInfo(name, 0)
                true
            } catch (ignored: PackageManager.NameNotFoundException) {
                false
            }
        }.toSet()
    }

    // endregion

    // region Mutations

    fun updateLedger(transform: (CollectionLedger) -> CollectionLedger): CollectionLedger {
        val next = synchronized(lock) {
            transform(collectionStore.load()).also { collectionStore.save(it) }
        }
        changed()
        return next
    }

    fun recordOutcome(
        batchIds: List<String>,
        kind: OutcomeKind,
        detail: String,
        target: DeliveryTarget,
        scene: Scene?,
        chatName: String?,
    ) {
        val at = Instant.now()
        val targetName = target.displayName(lang())
        synchronized(lock) {
            for (id in batchIds) {
                runCatching {
                    inbox.updateState(id) {
                        it.copy(
                            outcome = BatchOutcome(kind, detail, at),
                            targetName = targetName,
                            targetId = target.id,
                            chatName = chatName?.takeIf { name -> name.isNotBlank() } ?: it.chatName,
                            sceneId = scene?.id,
                            sceneName = scene?.name,
                        )
                    }
                }
            }
        }
        inbox.log("outcome ${kind.name} target=${target.id} batches=${batchIds.size}")
        changed()
    }

    fun deleteBatch(id: String) {
        synchronized(lock) {
            inbox.delete(id)
            collectionStore.save(collectionStore.load().detach(id))
        }
        changed()
    }

    /** Forgets the collection and deletes its batches; nothing else refers to them. */
    fun deleteCollection(id: String) {
        synchronized(lock) {
            val ledger = collectionStore.load()
            val collection = ledger.collection(id) ?: return
            val next = if (collection.status == CollectionStatus.DELIVERING) {
                ledger.recoverInterruptedDeliveries("").forget(id)
            } else {
                ledger.forget(id)
            }
            collectionStore.save(next)
            collection.batchIds.forEach { inbox.delete(it) }
        }
        changed()
    }

    // endregion

    private fun view(batch: ReadyBatch, ledger: CollectionLedger): BatchView {
        val items = batch.manifest.items
        val first = items.firstOrNull()?.displayName.orEmpty()
        val title = batch.state?.chatName?.takeIf { it.isNotBlank() }
            ?: if (items.size > 1) context.getString(R.string.batch_title_more, first, items.size - 1) else first
        return BatchView(
            id = batch.id,
            createdAt = batch.manifest.createdAt,
            title = title,
            itemCount = items.size,
            state = batch.state,
            collectionId = ledger.collectionOf(batch.id)?.id,
        )
    }

    private fun changed() {
        changes.update { it + 1 }
    }
}
