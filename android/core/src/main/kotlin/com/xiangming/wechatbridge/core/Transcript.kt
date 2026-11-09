package com.xiangming.wechatbridge.core

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One message from the TXT inside a WeChat export, ported from
 * `WeChatTranscriptRecord` (WeChatForward.swift). WeChat stamps wall-clock
 * minutes, so the time stays a `LocalDateTime`: it is rendered back exactly
 * as it was read, with no zone in between.
 */
data class TranscriptRecord(val sender: String, val time: LocalDateTime, val text: String)

/** A TXT found in an archive or shared directly. `records` is null when it is not WeChat's layout. */
data class Transcript(val path: String, val body: String, val records: List<TranscriptRecord>?)

object TranscriptParser {
    /** `·sender` then `yyyy年M月d日 HH:mm`, then the body up to the next header. */
    private val header = Regex(
        "^\u00B7([^\\n]+)\\n(\\d{4})年(\\d{1,2})月(\\d{1,2})日 (\\d{2}):(\\d{2})\\n",
        RegexOption.MULTILINE,
    )

    /** CRLF endings and a BOM ahead of the first header are container noise. */
    fun normalize(body: String): String = body.replace("\r\n", "\n").trim('\uFEFF')

    /** Fails closed: a body that does not start with a header is not a transcript. */
    fun parse(body: String): List<TranscriptRecord>? {
        val text = normalize(body)
        val matches = header.findAll(text).toList()
        if (matches.isEmpty() || matches[0].range.first != 0) return null
        return matches.mapIndexed { index, match ->
            val (sender, year, month, day, hour, minute) = match.destructured
            val time = try {
                LocalDateTime.of(year.toInt(), month.toInt(), day.toInt(), hour.toInt(), minute.toInt())
            } catch (ignored: DateTimeException) {
                // A header-shaped line with an impossible date is still a corrupt transcript.
                return null
            }
            val start = match.range.last + 1
            val end = if (index + 1 < matches.size) matches[index + 1].range.first else text.length
            TranscriptRecord(sender, time, text.substring(start, end).trim())
        }
    }
}

/**
 * Writes records back in WeChat's own layout, so a merged transcript reads like
 * one WeChat produced and parses again with [TranscriptParser]. Port of iOS
 * `TranscriptMerge`.
 */
object TranscriptMerge {
    private val stamp = DateTimeFormatter.ofPattern("yyyy'年'M'月'd'日' HH:mm", Locale.ROOT)

    fun render(records: List<TranscriptRecord>): String =
        records.joinToString("\n") { "\u00B7${it.sender}\n${stamp.format(it.time)}\n${it.text}\n" }

    /**
     * Time order, stable for equal minutes (ties are common and their original
     * order is the only order there is). A record that appears in two
     * overlapping forwards of the same chat is kept once.
     */
    fun merge(groups: List<List<TranscriptRecord>>): List<TranscriptRecord> {
        val seen = HashSet<String>()
        return groups.flatten()
            .filter { seen.add("${it.sender}\u0001${it.time}\u0001${it.text}") }
            .sortedBy { it.time }
    }
}

object TextDecoding {
    /**
     * UTF-8 first, as WeChat writes it. A TXT saved on a Chinese Windows
     * machine is often GB18030, which strict UTF-8 rejects outright.
     */
    fun decode(bytes: ByteArray): String {
        strict(bytes, Charsets.UTF_8)?.let { return it }
        runCatching { strict(bytes, Charset.forName("GB18030")) }.getOrNull()?.let { return it }
        return String(bytes, Charsets.UTF_8)
    }

    fun strictUtf8(bytes: ByteArray): String? = strict(bytes, Charsets.UTF_8)

    private fun strict(bytes: ByteArray, charset: Charset): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (ignored: CharacterCodingException) {
        null
    }
}
