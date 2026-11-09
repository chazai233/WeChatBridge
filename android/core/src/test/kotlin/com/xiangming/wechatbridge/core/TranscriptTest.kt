package com.xiangming.wechatbridge.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class TranscriptTest {
    @Test
    fun parsesWeChatLayoutIgnoringBomAndCrlf() {
        val records = TranscriptParser.parse(Fixtures.TRANSCRIPT)!!
        assertEquals(2, records.size)
        assertEquals("张三", records[0].sender)
        assertEquals(LocalDateTime.of(2026, 3, 5, 9, 15), records[0].time)
        assertEquals("大家早，今天下午三点开会。", records[0].text)
        assertEquals("[图片] photo1.jpg", records[1].text)
    }

    @Test
    fun textThatDoesNotStartWithAHeaderIsNotATranscript() {
        assertNull(TranscriptParser.parse("随手复制的一段话\n\u00B7张三\n2026年3月5日 09:15\n你好\n"))
        assertNull(TranscriptParser.parse(""))
    }

    @Test
    fun impossibleDateFailsClosed() {
        assertNull(TranscriptParser.parse("\u00B7张三\n2026年13月40日 09:15\n你好\n"))
    }

    @Test
    fun mergeOrdersByTimeAndDropsOverlap() {
        val first = TranscriptParser.parse(Fixtures.TRANSCRIPT)!!
        val second = TranscriptParser.parse(Fixtures.LATER_TRANSCRIPT)!!
        val merged = TranscriptMerge.merge(listOf(second, first))
        assertEquals(listOf("张三", "李四", "王五"), merged.map { it.sender })
    }

    @Test
    fun renderedRecordsParseBack() {
        val records = TranscriptParser.parse(Fixtures.TRANSCRIPT)!!
        assertEquals(records, TranscriptParser.parse(TranscriptMerge.render(records)))
    }

    @Test
    fun decodesGb18030WhenUtf8Fails() {
        val bytes = "群聊总结".toByteArray(charset("GB18030"))
        assertEquals("群聊总结", TextDecoding.decode(bytes))
    }
}
