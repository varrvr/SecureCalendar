package com.example.calendar

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.calendar.data.local.CalendarDatabase
import com.example.calendar.data.repository.EventRepositoryImpl
import com.example.calendar.domain.model.CalendarEvent
import com.example.calendar.domain.repository.SessionRepository
import java.time.Instant

/** Real, isolated Room calendar; not the user's installed database. */
class ComparisonTestCalendar(private val session: SessionRepository) : AutoCloseable {
    private val db = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, CalendarDatabase::class.java).build()
    val events = EventRepositoryImpl(db.eventDao(), session)
    suspend fun add(start: Long, end: Long, public: Boolean = true) {
        events.create(CalendarEvent(title = "PSI integration event", start = Instant.ofEpochSecond(start),
            end = Instant.ofEpochSecond(end), isPublic = public, ownerId = requireNotNull(session.activeUserId.value))).getOrThrow()
    }
    override fun close() = db.close()
}
