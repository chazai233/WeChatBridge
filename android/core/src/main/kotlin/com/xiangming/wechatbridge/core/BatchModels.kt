package com.xiangming.wechatbridge.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Which entry produced a batch. Raw values match macOS `ShareAction`, so a
 * manifest reads the same on every port. Android writes three of them:
 * [CUSTOM] for 「微信流」 and in-app paste (the destination is asked every
 * time), and [COLLECT] for 「分批收集到微信流」.
 */
@Serializable
enum class ShareAction {
    @SerialName("codex") CODEX,
    @SerialName("claude") CLAUDE,
    @SerialName("doubao") DOUBAO,
    @SerialName("qwen") QWEN,
    @SerialName("workBuddy") WORK_BUDDY,
    @SerialName("weSight") WE_SIGHT,
    @SerialName("deepSeekHarness") DEEP_SEEK_HARNESS,
    @SerialName("obsidian") OBSIDIAN,
    @SerialName("folder") FOLDER,
    @SerialName("collect") COLLECT,
    @SerialName("clipboard") CLIPBOARD,
    @SerialName("custom") CUSTOM,
}

@Serializable
data class ManifestItem(
    val id: String,
    /** Sanitised name, also the on-disk name inside `files/`. */
    val displayName: String,
    /** Relative to the batch directory; absolute paths are never persisted. */
    val relativePath: String,
    val contentType: String? = null,
    val byteCount: Long,
    /** Position in the share, kept so delivery preserves the order the host sent. */
    val itemIndex: Int,
    val attachmentIndex: Int = 0,
)

/** `Ready/<batchId>/manifest.json`: what the batch *is*. Written once, inside staging. */
@Serializable
data class BatchManifest(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    @SerialName("batchID") val batchId: String,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    val action: ShareAction,
    val items: List<ManifestItem>,
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val FILE_NAME = "manifest.json"
    }
}

@Serializable
enum class OutcomeKind {
    /** Handed to the target app, or written to the folder. */
    @SerialName("delivered") DELIVERED,
    @SerialName("copied") COPIED,
    @SerialName("failed") FAILED,
    @SerialName("expired") EXPIRED,
}

@Serializable
data class BatchOutcome(
    val kind: OutcomeKind,
    val detail: String? = null,
    @Serializable(with = InstantSerializer::class) val at: Instant,
)

/**
 * `Ready/<batchId>/state.json`: the app's own notes about a batch, kept beside
 * the manifest because the two have different lifetimes. Keys match macOS
 * `BatchState`; [targetId] is Android-only and ignored by other readers.
 */
@Serializable
data class BatchState(
    val schemaVersion: Int = 1,
    /** Null while the batch waits for the user to pick a destination. */
    val outcome: BatchOutcome? = null,
    val action: ShareAction? = null,
    val targetName: String? = null,
    val chatName: String? = null,
    @SerialName("sceneID") val sceneId: String? = null,
    val sceneName: String? = null,
    val targetId: String? = null,
) {
    companion object {
        const val FILE_NAME = "state.json"
    }
}
