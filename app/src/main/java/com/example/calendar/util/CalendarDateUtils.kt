
package com.example.calendar.util

import com.example.calendar.data.local.EventEntity
import com.example.calendar.domain.model.TimeInterval
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val ruLocale: Locale = Locale.forLanguageTag("ru-RU")

val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ISO_LOCAL_DATE

val timeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm")

val monthFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("LLLL yyyy", ruLocale)

fun EventEntity.startInstant(): Instant =
    Instant.ofEpochMilli(startTime)

fun EventEntity.endInstant(): Instant =
    Instant.ofEpochMilli(endTime)

fun EventEntity.overlaps(
    start: Instant,
    end: Instant
): Boolean =
    startTime < end.toEpochMilli() &&
            endTime > start.toEpochMilli()

fun dayBounds(
    date: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): TimeInterval =
    TimeInterval(
        start = date.atStartOfDay(zone).toInstant(),
        end = date.plusDays(1).atStartOfDay(zone).toInstant()
    )

fun formatTime(instant: Instant): String =
    instant.atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(timeFormatter)
