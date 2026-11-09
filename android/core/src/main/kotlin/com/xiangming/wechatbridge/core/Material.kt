package com.xiangming.wechatbridge.core

import java.io.File
import java.io.IOException

/** A file that travels with the chat text: shared directly or unpacked from a ZIP. */
data class Attachment(val file: File, val originalName: String, val kind: MediaKind)

/** One chat text: the TXT inside an export, a shared TXT or pasted text. */
data class TextSource(val name: String, val body: String, val records: List<TranscriptRecord>?)

/**
 * Everything one delivery works from, gathered from one or more batches in
 * the order they arrived. Chat texts in WeChat's layout are merged by time and
 * de-duplicated, so overlapping batches of a collection read as one chat.
 */
class Material(
    val texts: List<TextSource>,
    val attachments: List<Attachment>,
    /** Original ZIPs, kept so a knowledge note can link the untouched export. */
    val archives: List<File>,
    /** Names of shared files that could not be read; they still go along as attachments. */
    val unreadable: List<String>,
) {
    /** Non-null only when every text is a WeChat transcript. */
    val records: List<TranscriptRecord>? by lazy {
        if (texts.isNotEmpty() && texts.all { it.records != null }) {
            TranscriptMerge.merge(texts.map { it.records.orEmpty() })
        } else {
            null
        }
    }

    val chatText: String by lazy {
        val merged = records
        when {
            texts.isEmpty() -> ""
            // A single export goes out exactly as WeChat wrote it.
            texts.size == 1 -> TranscriptParser.normalize(texts[0].body).trim()
            merged != null -> TranscriptMerge.render(merged).trim()
            else -> texts.joinToString("\n\n") { TranscriptParser.normalize(it.body).trim() }
        }
    }

    val messageCount: Int? get() = records?.size

    val isEmpty: Boolean get() = texts.isEmpty() && attachments.isEmpty()

    /** Senders in order of first appearance. */
    fun participants(): List<String> =
        records.orEmpty().map { it.sender.trim() }.filter { it.isNotEmpty() }.distinct()
}

object Materializer {
    /**
     * Reads [batches] into a [Material]. ZIP media is unpacked under
     * [workDirectory], which the caller owns and clears later: the receiving
     * app may still be reading those files after the share returns.
     */
    fun materialize(batches: List<ReadyBatch>, workDirectory: File): Material {
        val texts = mutableListOf<TextSource>()
        val attachments = mutableListOf<Attachment>()
        val archives = mutableListOf<File>()
        val unreadable = mutableListOf<String>()
        for (batch in batches) {
            for (item in batch.manifest.items) {
                val file = batch.file(item)
                if (!file.isFile) {
                    unreadable += item.displayName
                    continue
                }
                when (val kind = MediaKind.of(item.displayName, item.contentType)) {
                    MediaKind.ZIP -> {
                        try {
                            val contents = WeChatArchive.read(file)
                            contents.transcript?.let { texts += TextSource(item.displayName, it.body, it.records) }
                            val destination = File(workDirectory, "${batch.id}/${item.id}")
                            val skip = setOfNotNull(contents.transcript?.path)
                            WeChatArchive.extract(file, destination, skip).forEach {
                                attachments += Attachment(it.file, it.leafName, MediaKind.of(it.leafName))
                            }
                            archives += file
                        } catch (ignored: IOException) {
                            // Not an export this build can read; the ZIP itself still goes along.
                            unreadable += item.displayName
                            attachments += Attachment(file, item.displayName, MediaKind.ZIP)
                        }
                    }
                    MediaKind.TEXT -> {
                        if (file.length() > WeChatArchive.MAX_TEXT_BYTES) {
                            attachments += Attachment(file, item.displayName, kind)
                        } else {
                            val body = TextDecoding.decode(file.readBytes())
                            texts += TextSource(item.displayName, body, TranscriptParser.parse(body))
                        }
                    }
                    else -> attachments += Attachment(file, item.displayName, kind)
                }
            }
        }
        return Material(texts, attachments, archives, unreadable)
    }
}

/** First and last message of a collection, so the user can see where to continue in WeChat. */
data class BatchInsight(val messageCount: Int?, val first: TranscriptRecord?, val last: TranscriptRecord?)

object BatchInsights {
    /** Reads transcripts only; nothing is unpacked. */
    fun read(batches: List<ReadyBatch>): BatchInsight {
        val groups = mutableListOf<List<TranscriptRecord>?>()
        for (batch in batches) {
            for (item in batch.manifest.items) {
                val file = batch.file(item)
                when (MediaKind.of(item.displayName, item.contentType)) {
                    MediaKind.ZIP -> groups.add(runCatching { WeChatArchive.read(file).transcript?.records }.getOrNull())
                    MediaKind.TEXT -> groups.add(
                        runCatching { TranscriptParser.parse(TextDecoding.decode(file.readBytes())) }.getOrNull(),
                    )
                    else -> Unit
                }
            }
        }
        val records = TranscriptMerge.merge(groups.filterNotNull())
        val complete = groups.isNotEmpty() && groups.all { it != null }
        return BatchInsight(if (complete) records.size else null, records.firstOrNull(), records.lastOrNull())
    }
}
