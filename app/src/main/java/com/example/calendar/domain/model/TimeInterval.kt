package com.example.calendar.domain.model

import java.time.Instant

data class TimeInterval(
    val start: Instant,
    val end: Instant
)
