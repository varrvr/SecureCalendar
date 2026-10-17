package com.example.calendar.presentation.calendar

import com.example.calendar.data.local.EventEntity
import com.example.calendar.domain.model.TimeInterval
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.calendar.data.local.CalendarDatabase
import com.example.calendar.domain.model.CalendarTab
import endInstant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import startInstant
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

// -------------------- VIEWMODEL --------------------

class CalendarViewModel(application: Application) :
    AndroidViewModel(application) {

    private val dao = CalendarDatabase
        .getInstance(application)
        .eventDao()

    val events = dao.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    var displayedMonth by mutableStateOf(YearMonth.now())
        private set

    var selectedDate by mutableStateOf(LocalDate.now())
        private set

    var selectedTab by mutableStateOf(CalendarTab.CALENDAR)
        private set

    var editingEvent by mutableStateOf<EventEntity?>(null)
        private set

    var showEditor by mutableStateOf(false)
        private set

    fun selectTab(tab: CalendarTab) {
        selectedTab = tab
    }

    fun selectDate(date: LocalDate) {
        selectedDate = date
        displayedMonth = YearMonth.from(date)
    }

    fun previousMonth() {
        displayedMonth = displayedMonth.minusMonths(1)
        selectedDate = displayedMonth.atDay(1)
    }

    fun nextMonth() {
        displayedMonth = displayedMonth.plusMonths(1)
        selectedDate = displayedMonth.atDay(1)
    }

    fun openCreateEditor() {
        editingEvent = null
        showEditor = true
    }

    fun openEditEditor(event: EventEntity) {
        editingEvent = event
        showEditor = true
    }

    fun closeEditor() {
        showEditor = false
        editingEvent = null
    }

    fun saveEvent(
        existing: EventEntity?,
        title: String,
        start: Instant,
        end: Instant,
        isPublic: Boolean
    ) {
        require(title.isNotBlank())
        require(end.isAfter(start))

        viewModelScope.launch {
            val entity = EventEntity(
                id = existing?.id ?: 0,
                title = title.trim(),
                startTime = start.toEpochMilli(),
                endTime = end.toEpochMilli(),
                isPublic = isPublic
            )

            if (existing == null) {
                dao.insert(entity)
            } else {
                dao.update(entity)
            }

            closeEditor()
        }
    }

    fun deleteEvent(event: EventEntity) {
        viewModelScope.launch {
            dao.delete(event)
            closeEditor()
        }
    }

    suspend fun getIntervalsForPsi(
        period: TimeInterval,
        publicOnly: Boolean
    ): List<TimeInterval> {

        val matchingEvents = if (publicOnly) {
            dao.getPublicForPeriod(
                period.start.toEpochMilli(),
                period.end.toEpochMilli()
            )
        } else {
            dao.getForPeriod(
                period.start.toEpochMilli(),
                period.end.toEpochMilli()
            )
        }

        return matchingEvents.mapNotNull { event ->

            val start = kotlin.comparisons.maxOf(
                event.startInstant(),
                period.start
            )

            val end = kotlin.comparisons.minOf(
                event.endInstant(),
                period.end
            )

            if (end.isAfter(start)) {
                TimeInterval(start, end)
            } else {
                null
            }
        }
    }
}