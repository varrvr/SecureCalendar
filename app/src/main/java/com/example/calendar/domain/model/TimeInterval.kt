package com.example.calendar.domain.model

import java.time.Instant

data class TimeInterval(
    val start: Instant,
    val end: Instant
) {
    init { require(end.isAfter(start)) { "Interval end must be after start" } }
}
