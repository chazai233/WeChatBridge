package com.xiangming.wechatbridge.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.UUID

/** One thing handed to the app: a shared file, a `content://` stream or pasted text. */
class IncomingItem(
    val displayName: String,
    val contentType: String?,
    val open: () -> InputStream,
) {
    companion object {
        fun text(displayName: String, text: String): IncomingItem =
            IncomingItem(displayName, "text/plain") { text.toByteArray(Charsets.UTF_8).inputStream() }
    }
}

class InboxException(val reason: Reason, message: String, cause: Throwable? = null) :
    IOException(message, cause) {
    enum class Reason { EMPTY, TOO_LARGE, IO }
}

class ReadyBatch(val directory: File, val manifest: BatchManifest, val state: BatchState?) {
    val id: String get() = manifest.batchId
    fun file(item: ManifestItem): File = File(directory, item.relativePath)
    val files: List<File> get() = manifest.items.map { file(it) }
}

/**
 * The Inbox contract from `shared/README.md`, rooted in the app's private
 * storage:
 *
 * ```
 * Inbox/
 * ├── Staging/<id>.staging/   filled here, invisible to readers
 * ├── Ready/<id>/             manifest.json, state.json, files/
 * ├── Failed/<id>.json
 * └── Logs/
 * ```
 *
 * A batch appears in `Ready/` only through one directory rename, so a crash
 * mid-copy leaves a staging tree to prune, never a half batch.
 */
class Inbox(
    val root: File,
    private val maxFileBytes: Long = MAX_FILE_BYTES,
    private val maxBatchBytes: Long = MAX_BATCH_BYTES,
    private val clock: () -> Instant = Instant::now,
) {
    val staging = File(root, "Staging")
    val ready = File(root, "Ready")
    val failed = File(root, "Failed")
    val logs = File(root, "Logs")

    fun ensureCreated() {
        listOf(staging, ready, failed, logs).forEach { it.mkdirs() }
    }

    fun commit(
        items: List<IncomingItem>,
        action: ShareAction,
        initialState: BatchState? = null,
    ): ReadyBatch {
        if (items.isEmpty()) throw InboxException(InboxException.Reason.EMPTY, "Nothing to import")
        ensureCreated()
        val id = UUID.randomUUID().toString().uppercase()
        val stage = File(staging, "$id.staging")
        val filesDirectory = File(stage, "files")
        try {
            if (!filesDirectory.mkdirs()) throw IOException("Cannot create ${filesDirectory.path}")
            val taken = mutableSetOf<String>()
            var total = 0L
            val manifestItems = items.mapIndexed { index, item ->
                val name = DisplayName.unique(DisplayName.sanitize(item.displayName), taken)
                taken += name
                val output = File(filesDirectory, name)
                val bytes = item.open().use { input ->
                    FileOutputStream(output).use { sink ->
                        copyLimited(input, sink, minOf(maxFileBytes, maxBatchBytes - total))
                    }
                }
                total += bytes
                ManifestItem(
                    id = UUID.randomUUID().toString().uppercase(),
                    displayName = name,
                    relativePath = "files/$name",
                    contentType = item.contentType,
                    byteCount = bytes,
                    itemIndex = index,
                )
            }
            val manifest = BatchManifest(batchId = id, createdAt = clock(), action = action, items = manifestItems)
            AtomicFiles.writeText(File(stage, BatchManifest.FILE_NAME), BridgeJson.encodeToString(manifest))
            if (initialState != null) {
                AtomicFiles.writeText(File(stage, BatchState.FILE_NAME), BridgeJson.encodeToString(initialState))
            }
            val destination = File(ready, id)
            Files.move(stage.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
            return ReadyBatch(destination, manifest, initialState)
        } catch (error: Exception) {
            stage.deleteRecursively()
            recordFailure(id, error)
            if (error is InboxException) throw error
            throw InboxException(InboxException.Reason.IO, error.message ?: error.toString(), error)
        }
    }

    /** Newest first. A batch whose manifest cannot be read is skipped, not fatal. */
    fun batches(): List<ReadyBatch> =
        (ready.listFiles() ?: emptyArray())
            .filter { it.isDirectory }
            .mapNotNull { read(it) }
            .sortedByDescending { it.manifest.createdAt }

    fun batch(id: String): ReadyBatch? {
        if (!isSafeId(id)) return null
        val directory = File(ready, id)
        return if (directory.isDirectory) read(directory) else null
    }

    fun writeState(id: String, state: BatchState) {
        val batch = batch(id) ?: throw IOException("No batch $id")
        AtomicFiles.writeText(File(batch.directory, BatchState.FILE_NAME), BridgeJson.encodeToString(state))
    }

    fun updateState(id: String, transform: (BatchState) -> BatchState): BatchState {
        val batch = batch(id) ?: throw IOException("No batch $id")
        val next = transform(batch.state ?: BatchState(action = batch.manifest.action))
        writeState(id, next)
        return next
    }

    fun delete(id: String): Boolean {
        if (!isSafeId(id)) return false
        val directory = File(ready, id)
        return directory.isDirectory && directory.deleteRecursively()
    }

    /** Removes staging trees no live import can still be filling. */
    fun pruneStaging(olderThanMillis: Long = 30 * 60 * 1000L, now: Long = System.currentTimeMillis()): Int {
        var removed = 0
        for (candidate in staging.listFiles() ?: emptyArray()) {
            val newest = candidate.walkTopDown().maxOfOrNull { it.lastModified() } ?: continue
            if (now - newest > olderThanMillis && candidate.deleteRecursively()) removed++
        }
        return removed
    }

    fun log(message: String) {
        runCatching {
            logs.mkdirs()
            File(logs, "android.log").appendText("${InstantSerializer.format(clock())} $message\n")
        }
    }

    private fun read(directory: File): ReadyBatch? {
        val manifest = runCatching {
            BridgeJson.decodeFromString<BatchManifest>(File(directory, BatchManifest.FILE_NAME).readText())
        }.getOrNull() ?: return null
        if (manifest.batchId != directory.name) return null
        val stateFile = File(directory, BatchState.FILE_NAME)
        val state = if (stateFile.isFile) {
            runCatching { BridgeJson.decodeFromString<BatchState>(stateFile.readText()) }.getOrNull()
        } else {
            null
        }
        return ReadyBatch(directory, manifest, state)
    }

    private fun copyLimited(input: InputStream, output: FileOutputStream, budget: Long): Long {
        val buffer = ByteArray(128 * 1024)
        var copied = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            copied += count
            if (copied > budget) throw InboxException(InboxException.Reason.TOO_LARGE, "Share is too large")
            output.write(buffer, 0, count)
        }
        output.fd.sync()
        return copied
    }

    private fun recordFailure(id: String, error: Exception) {
        runCatching {
            failed.mkdirs()
            val record = FailureRecord(batchId = id, createdAt = clock(), message = error.message ?: error.toString())
            AtomicFiles.writeText(File(failed, "$id.json"), BridgeJson.encodeToString(record))
        }
    }

    private fun isSafeId(id: String) = id.isNotEmpty() && id.all { it.isLetterOrDigit() || it == '-' }

    companion object {
        const val MAX_FILE_BYTES = 1L shl 30
        const val MAX_BATCH_BYTES = 1L shl 30
    }
}

/** `Failed/<id>.json`: why an import never reached `Ready/`. */
@Serializable
internal data class FailureRecord(
    val schemaVersion: Int = BatchManifest.CURRENT_SCHEMA_VERSION,
    @SerialName("batchID") val batchId: String,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    val message: String,
)