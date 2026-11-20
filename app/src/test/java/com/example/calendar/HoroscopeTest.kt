package com.example.calendar.horoscope

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test


class HoroscopeTest {

    private val generator = HoroscopeGenerator(
        corpus = SecureCalendarCorpus.build(),
        salt = ByteArray(32) { it.toByte() },
    )

    @Test
    fun `детерминизм`() {
        repeat(50) { i ->
            val a = generator.forMeeting("event-$i").text
            val b = generator.forMeeting("event-$i").text
            assertEquals("determinism broken for event-$i", a, b)
        }
    }

    @Test
    fun `разные nonce дают разные тексты`() {
        val texts = (0L..99L).map { generator.forMeeting("event-1", it).text }.toSet()
        assertTrue("too few unique: ${texts.size}", texts.size >= 80)
    }

    @Test
    fun `никогда не пусто`() {
        repeat(1000) { i ->
            val t = generator.forMeeting("e-$i").text
            assertTrue("empty for e-$i", t.isNotBlank())
            assertTrue("no trailing dot: $t", t.endsWith("."))
        }
    }
}