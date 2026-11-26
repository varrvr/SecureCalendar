package com.example.calendar.presentation.comparison

import com.example.calendar.domain.model.CalendarEvent
import com.example.calendar.domain.model.TimeInterval
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val comparisonZone: ZoneId = ZoneId.of("Europe/Minsk")

fun comparisonPeriod(start: LocalDate, endInclusive: LocalDate): TimeInterval = TimeInterval(
    start.atStartOfDay(comparisonZone).toInstant(),
    endInclusive.plusDays(1).atStartOfDay(comparisonZone).toInstant()
)

fun formatComparisonTime(instant: Instant): String = DateTimeFormatter
    .ofPattern("dd.MM.yyyy HH:mm", Locale.forLanguageTag("ru-RU"))
    .withZone(comparisonZone).format(instant)

data class NamedComparisonResult(val interval: TimeInterval, val meetingTitles: List<String>)

/** Enrich PSI results only on this device. Private events and mere boundary touches are excluded. */
fun namedComparisonResults(intervals: List<TimeInterval>, events: List<CalendarEvent>): List<NamedComparisonResult> =
    intervals.map { interval ->
        NamedComparisonResult(interval, events.asSequence()
            .filter { it.isPublic && it.start < interval.end && it.end > interval.start }
            .sortedBy { it.start }
            .map { it.title }
            .distinct().toList())
    }
