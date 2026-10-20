package com.example.calendar

import com.example.calendar.crypto.SlotConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SlotConverterTest {
    @Test fun intervalsUseUtcAlignedSlotsAndDeduplicateOverlaps() {
        val start = Instant.parse("2026-01-01T23:55:00Z")
        val end = Instant.parse("2026-01-02T00:20:00Z")
        val slots = SlotConverter.toSlots(start, end)
        assertEquals(3, slots.size)
        assertTrue(slots.containsAll(SlotConverter.toSlots(Instant.parse("2026-01-02T00:00:00Z"), end)))
        assertEquals(3, (slots + slots).size)
    }
}
