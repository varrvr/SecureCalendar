package com.example.calendar.domain.model

import java.time.Instant

data class CalendarEvent(
    val id: Long = 0,
    val title: String,
    val start: Instant,
    val end: Instant,
    val isPublic: Boolean,
    val ownerId: Long
) {
    init { require(end.isAfter(start)); require(ownerId > 0) }
}
