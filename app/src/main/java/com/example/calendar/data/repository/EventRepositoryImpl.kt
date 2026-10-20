package com.example.calendar.data.repository

import com.example.calendar.data.local.EventDao
import com.example.calendar.data.local.EventEntity
import com.example.calendar.domain.model.CalendarEvent
import com.example.calendar.domain.model.TimeInterval
import com.example.calendar.domain.repository.EventRepository
import com.example.calendar.domain.repository.SessionRepository
import com.example.calendar.util.endInstant
import com.example.calendar.util.startInstant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import java.time.Instant

class EventRepositoryImpl(
    private val eventDao: EventDao,
    private val sessions: SessionRepository
) : EventRepository {
    override fun observeEvents(): Flow<List<CalendarEvent>> = sessions.activeUserId.flatMapLatest { ownerId ->
        if (ownerId == null) flowOf(emptyList()) else eventDao.observeAll(ownerId).map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun create(event: CalendarEvent): Result<Long> = result {
        val ownerId = requireNotNull(sessions.activeUserId.value) { "No active local session" }
        require(event.ownerId == ownerId && event.title.isNotBlank() && event.end.isAfter(event.start)) { "Invalid or unowned event" }
        eventDao.insertOwned(event.title.trim(), event.start.toEpochMilli(), event.end.toEpochMilli(), event.isPublic, ownerId)
    }

    override suspend fun update(event: CalendarEvent): Result<Unit> = result {
        val ownerId = requireNotNull(sessions.activeUserId.value) { "No active local session" }
        require(event.ownerId == ownerId && event.id > 0 && event.title.isNotBlank() && event.end.isAfter(event.start)) { "Invalid or unowned event" }
        check(eventDao.updateOwned(ownerId, event.id, event.title.trim(), event.start.toEpochMilli(), event.end.toEpochMilli(), event.isPublic) == 1) { "Event not found for active user" }
    }

    override suspend fun delete(eventId: Long): Result<Unit> = result {
        val ownerId = requireNotNull(sessions.activeUserId.value) { "No active local session" }
        check(eventDao.deleteOwned(ownerId, eventId) == 1) { "Event not found for active user" }
    }

    override suspend fun getIntervals(period: TimeInterval, publicOnly: Boolean): List<TimeInterval> {
        val ownerId = sessions.activeUserId.value ?: return emptyList()
        require(period.end.isAfter(period.start))
        val start = period.start.toEpochMilli(); val end = period.end.toEpochMilli()
        val rows = if (publicOnly) eventDao.getPublicForPeriod(ownerId, start, end) else eventDao.getForPeriod(ownerId, start, end)
        return rows.mapNotNull { row ->
            val clippedStart = maxOf(row.startInstant(), period.start)
            val clippedEnd = minOf(row.endInstant(), period.end)
            if (clippedEnd.isAfter(clippedStart)) TimeInterval(clippedStart, clippedEnd) else null
        }
    }

    private fun EventEntity.toDomain() = CalendarEvent(id, title, startInstant(), endInstant(), isPublic, ownerId)

    private suspend fun <T> result(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}
