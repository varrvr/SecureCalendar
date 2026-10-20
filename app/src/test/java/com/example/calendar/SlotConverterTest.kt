package com.example.calendar

import com.example.calendar.crypto.SlotConverter
import com.example.calendar.domain.model.TimeInterval
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

    @Test fun intervalsAreClippedSortedAndDeduplicatedInSelectedPeriod() {
        val period = TimeInterval(Instant.parse("2026-03-01T00:00:00Z"), Instant.parse("2026-03-01T01:00:00Z"))
        val intervals = listOf(
            TimeInterval(Instant.parse("2026-02-28T23:55:00Z"), Instant.parse("2026-03-01T00:20:00Z")),
            TimeInterval(Instant.parse("2026-03-01T00:15:00Z"), Instant.parse("2026-03-01T00:30:00Z")),
            TimeInterval(Instant.parse("2026-03-01T00:45:00Z"), Instant.parse("2026-03-01T02:00:00Z"))
        )
        val slots = SlotConverter.toSlots(intervals, period)
        val base = Math.floorDiv(period.start.epochSecond, SlotConverter.SLOT_SECONDS)
        assertEquals(listOf(base, base + 1, base + 3), slots)
    }

    @Test fun slotsBeforeUnixEpochUseFloorDivision() {
        val slots = SlotConverter.toSlots(
            Instant.parse("1969-12-31T23:50:00Z"),
            Instant.parse("1970-01-01T00:00:00Z")
        )
        assertEquals(listOf(-1L), slots.toList())
    }
}
