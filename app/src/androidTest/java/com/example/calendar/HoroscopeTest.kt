package com.example.calendar.horoscope

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
            assertEquals(a, b, "determinism broken for event-$i")
        }
    }

    @Test
    fun `разные nonce дают разные тексты`() {
        val texts = (0L..99L).map { generator.forMeeting("event-1", it).text }.toSet()
        assertTrue(texts.size >= 80, "too few unique: ${texts.size}")
    }

    @Test
    fun `никогда не пусто`() {
        repeat(1000) { i ->
            val t = generator.forMeeting("e-$i").text
            assertTrue(t.isNotBlank(), "empty for e-$i")
            assertTrue(t.endsWith("."), "no trailing dot: $t")
        }
    }
}