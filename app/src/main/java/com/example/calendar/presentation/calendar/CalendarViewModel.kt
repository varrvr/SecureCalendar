
package com.example.calendar.presentation.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calendar.domain.model.CalendarEvent
import com.example.calendar.domain.model.CalendarTab
import com.example.calendar.domain.model.TimeInterval
import com.example.calendar.domain.repository.EventRepository
import com.example.calendar.domain.repository.SessionRepository
import com.example.calendar.presentation.editor.EventEditorUiState
import com.example.calendar.util.endInstant
import com.example.calendar.util.startInstant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

class CalendarViewModel(
    private val eventRepository: EventRepository,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    val events = eventRepository.observeEvents().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    var displayedMonth by mutableStateOf(YearMonth.now())
        private set

    var selectedDate by mutableStateOf(LocalDate.now())
        private set

    var editorState by mutableStateOf<EventEditorUiState?>(null)
        private set

    private var editedEvent: CalendarEvent? = null
    private var editorOwnerId: Long? = null
    private var previousOwnerId: Long? = sessionRepository.activeUserId.value

    init {
        viewModelScope.launch {
            sessionRepository.activeUserId.collect { userId ->
                if (previousOwnerId != userId) {
                    editorState = null
                    editedEvent = null
                    editorOwnerId = null
                    previousOwnerId = userId
                }
            }
        }
    }

    fun selectDate(date: LocalDate) {
        selectedDate = date
        displayedMonth = YearMonth.from(date)
    }

    fun previousMonth() {
        selectDate(displayedMonth.minusMonths(1).atDay(1))
    }

    fun nextMonth() {
        selectDate(displayedMonth.plusMonths(1).atDay(1))
    }

    fun goToday() {
        selectDate(LocalDate.now())
    }

    fun openCreateEditor() {
        editorOwnerId = sessionRepository.activeUserId.value
        if (editorOwnerId == null) return
        editedEvent = null
        editorState = EventEditorUiState(
            startDate = selectedDate,
            endDate = selectedDate
        )
    }

    fun openEditEditor(event: CalendarEvent) {
        if (event.ownerId != sessionRepository.activeUserId.value) return
        editorOwnerId = event.ownerId
        editedEvent = event

        val start = event.start.atZone(ZoneId.systemDefault())

        val end = event.end.atZone(ZoneId.systemDefault())

        editorState = EventEditorUiState(
            isEditing = true,
            title = event.title,
            startDate = start.toLocalDate(),
            startTime = start.toLocalTime(),
            endDate = end.toLocalDate(),
            endTime = end.toLocalTime(),
            isPublic = event.isPublic
        )
    }

    fun closeEditor() {
        if (editorState?.isSaving != true) {
            editorState = null
            editedEvent = null
            editorOwnerId = null
        }
    }

    fun changeTitle(value: String) {
        changeEditor {
            copy(
                title = value,
                titleError = null,
                saveError = null
            )
        }
    }

    fun changeStartDate(value: LocalDate) {
        changeEditor {
            copy(
                startDate = value,
                intervalError = null
            )
        }
    }

    fun changeStartTime(value: LocalTime) {
        changeEditor {
            copy(
                startTime = value,
                intervalError = null
            )
        }
    }

    fun changeEndDate(value: LocalDate) {
        changeEditor {
            copy(
                endDate = value,
                intervalError = null
            )
        }
    }

    fun changeEndTime(value: LocalTime) {
        changeEditor {
            copy(
                endTime = value,
                intervalError = null
            )
        }
    }

    fun changePublic(value: Boolean) {
        changeEditor {
            copy(isPublic = value)
        }
    }

    private fun changeEditor(
        block: EventEditorUiState.() -> EventEditorUiState
    ) {
        editorState?.let {
            if (!it.isSaving) {
                editorState = it.block()
            }
        }
    }

    fun saveEditor() {
        val state = editorState ?: return
        if (state.isSaving) return

        val ownerId = editorOwnerId
        if (ownerId == null || ownerId != sessionRepository.activeUserId.value) {
            editorState = null
            editedEvent = null
            return
        }
        val start = state.startDate
            .atTime(state.startTime)
            .atZone(ZoneId.systemDefault())
            .toInstant()

        val end = state.endDate
            .atTime(state.endTime)
            .atZone(ZoneId.systemDefault())
            .toInstant()

        val titleError = if (state.title.isBlank()) {
            "Введите название встречи"
        } else {
            null
        }

        val intervalError = if (!end.isAfter(start)) {
            "Окончание должно быть позже начала"
        } else {
            null
        }

        if (titleError != null || intervalError != null) {
            editorState = state.copy(
                titleError = titleError,
                intervalError = intervalError
            )
            return
        }

        editorState = state.copy(
            isSaving = true,
            titleError = null,
            intervalError = null,
            saveError = null
        )

        viewModelScope.launch {
            try {
                val event = CalendarEvent(
                    id = editedEvent?.id ?: 0L,
                    title = state.title.trim(),
                    start = start,
                    end = end,
                    isPublic = state.isPublic,
                    ownerId = ownerId
                )

                if (editedEvent == null) {
                    eventRepository.create(event).getOrThrow()
                } else {
                    eventRepository.update(event).getOrThrow()
                }

                editorState = null
                editedEvent = null
                editorOwnerId = null
            } catch (e: Exception) {
                editorState = editorState?.copy(
                    isSaving = false,
                    saveError = "Не удалось сохранить встречу. Повторите попытку."
                )
            }
        }
    }

    fun deleteEditor() {
        val event = editedEvent ?: return
        val state = editorState ?: return

        if (state.isSaving) return

        editorState = state.copy(isSaving = true)

        viewModelScope.launch {
            try {
                if (event.ownerId != sessionRepository.activeUserId.value) return@launch
                eventRepository.delete(event.id).getOrThrow()
                editedEvent = null
                editorState = null
                editorOwnerId = null
            } catch (e: Exception) {
                editorState = editorState?.copy(
                    isSaving = false,
                    saveError = "Не удалось удалить встречу"
                )
            }
        }
    }

    suspend fun getIntervalsForPsi(
        period: TimeInterval,
        publicOnly: Boolean
    ): List<TimeInterval> {
        return eventRepository.getIntervals(period, publicOnly)
    }

    fun selectTab(calendar: CalendarTab) {}
}
