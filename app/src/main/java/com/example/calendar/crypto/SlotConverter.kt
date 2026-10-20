
package com.example.calendar.crypto

import java.time.Instant
import com.example.calendar.domain.model.TimeInterval

object SlotConverter {

    const val SLOT_MINUTES = 15L
    const val SLOT_SECONDS = SLOT_MINUTES * 60L

    private const val MAX_SLOTS = 100_000L

    fun toSlots(
        start: Instant,
        end: Instant
    ): Set<Long> {
        require(start < end) {
            "Start must be before end"
        }

        val firstSlot = Math.floorDiv(
            start.epochSecond,
            SLOT_SECONDS
        )

        val lastInstant = end.minusNanos(1)

        val lastSlot = Math.floorDiv(
            lastInstant.epochSecond,
            SLOT_SECONDS
        )

        val count = Math.addExact(
            Math.subtractExact(lastSlot, firstSlot),
            1L
        )

        require(count <= MAX_SLOTS) {
            "Too many calendar slots"
        }

        return (firstSlot..lastSlot).toSet()
    }

    /** Clips intervals to the selected UTC period, deduplicates slots, and returns canonical order. */
    fun toSlots(
        intervals: Iterable<TimeInterval>,
        period: TimeInterval
    ): List<Long> {
        val slots = sortedSetOf<Long>()
        intervals.forEach { interval ->
            val clippedStart = maxOf(interval.start, period.start)
            val clippedEnd = minOf(interval.end, period.end)
            if (clippedStart.isBefore(clippedEnd)) {
                slots += toSlots(clippedStart, clippedEnd)
            }
        }
        return slots.toList()
    }

    fun toInstant(slot: Long): Instant {
        return Instant.ofEpochSecond(
            Math.multiplyExact(slot, SLOT_SECONDS)
        )
    }

    fun intersect(
        first: Set<Long>,
        second: Set<Long>
    ): Set<Long> {
        return first.intersect(second)
    }

    fun freeSlots(
        allowedSlots: Set<Long>,
        busySlots: Set<Long>
    ): Set<Long> {
        return allowedSlots - busySlots
    }

    fun encodeSlot(slot: Long): ByteArray {
        val bytes = ByteArray(8)

        for (i in 0 until 8) {
            bytes[7 - i] =
                ((slot ushr (i * 8)) and 0xFFL).toByte()
        }

        return bytes
    }
}
