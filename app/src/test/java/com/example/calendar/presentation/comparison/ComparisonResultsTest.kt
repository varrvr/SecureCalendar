package com.example.calendar.presentation.comparison

import com.example.calendar.domain.model.CalendarEvent
import com.example.calendar.domain.model.TimeInterval
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ComparisonResultsTest {
    @Test fun `results show Minsk time even across UTC midnight`() {
        assertEquals("26.11.2026 00:30", formatComparisonTime(Instant.parse("2026-11-25T21:30:00Z")))
        assertEquals("25.11.2026 13:00", formatComparisonTime(Instant.parse("2026-11-25T10:00:00Z")))
    }

    @Test fun `inclusive local day covers its full Minsk midnight boundaries`() {
        val period = comparisonPeriod(LocalDate.of(2026, 11, 25), LocalDate.of(2026, 11, 25))
        assertEquals(Instant.parse("2026-11-24T21:00:00Z"), period.start)
        assertEquals(Instant.parse("2026-11-25T21:00:00Z"), period.end)
    }

    @Test fun `period handles month and year transitions`() {
        val period = comparisonPeriod(LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1))
        assertEquals(Instant.parse("2026-12-30T21:00:00Z"), period.start)
        assertEquals(Instant.parse("2027-01-01T21:00:00Z"), period.end)
    }

    @Test fun `all overlapping public meeting titles accompany an intersection`() {
        val interval = interval("10:00", "11:00")
        val events = listOf(event("Later", "10:30", "11:30"), event("Earlier", "09:30", "10:30"),
            event("Earlier", "09:45", "10:15"), event("Private", "10:00", "11:00", public = false))
        val result = namedComparisonResults(listOf(interval), events).single()
        assertEquals(interval, result.interval)
        assertEquals(listOf("Earlier", "Later"), result.meetingTitles)
    }

    @Test fun `boundary touches and unrelated meetings are excluded`() {
        val result = namedComparisonResults(listOf(interval("10:00", "11:00")), listOf(
            event("Ends at boundary", "09:00", "10:00"), event("Starts at boundary", "11:00", "12:00"),
            event("Other time", "15:00", "16:00"))).single()
        assertTrue(result.meetingTitles.isEmpty())
    }

    @Test fun `missing or deleted local events do not remove the actual PSI result`() {
        val intervals = listOf(interval("10:00", "10:15"), interval("11:00", "11:15"))
        val results = namedComparisonResults(intervals, emptyList())
        assertEquals(intervals, results.map { it.interval })
        assertTrue(results.all { it.meetingTitles.isEmpty() })
    }

    private fun instant(time: String) = Instant.parse("2026-11-25T${time}:00Z")
    private fun interval(start: String, end: String) = TimeInterval(instant(start), instant(end))
    private fun event(title: String, start: String, end: String, public: Boolean = true) =
        CalendarEvent(title = title, start = instant(start), end = instant(end), isPublic = public, ownerId = 1)
}
