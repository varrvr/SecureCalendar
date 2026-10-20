package com.example.calendar.domain.repository

import com.example.calendar.domain.model.CalendarEvent
import com.example.calendar.domain.model.TimeInterval
import kotlinx.coroutines.flow.Flow

interface EventRepository {
    fun observeEvents(): Flow<List<CalendarEvent>>
    suspend fun create(event: CalendarEvent): Result<Long>
    suspend fun update(event: CalendarEvent): Result<Unit>
    suspend fun delete(eventId: Long): Result<Unit>
    suspend fun getIntervals(period: TimeInterval, publicOnly: Boolean = false): List<TimeInterval>
}
