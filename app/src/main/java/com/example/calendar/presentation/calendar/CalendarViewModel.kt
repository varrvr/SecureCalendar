
package com.example.calendar.presentation.calendar

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.calendar.data.local.CalendarDatabase
import com.example.calendar.data.local.EventEntity
import com.example.calendar.domain.model.CalendarTab
import com.example.calendar.domain.model.TimeInterval
import com.example.calendar.presentation.editor.EventEditorUiState
import com.example.calendar.util.endInstant
import com.example.calendar.util.startInstant
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

class CalendarViewModel(
    application: Application
) : AndroidViewModel(application) {

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

    var editorState by mutableStateOf<EventEditorUiState?>(null)
        private set

    private var editedEvent: EventEntity? = null

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
        editedEvent = null
        editorState = EventEditorUiState(
            startDate = selectedDate,
            endDate = selectedDate
        )
    }

    fun openEditEditor(event: EventEntity) {
        editedEvent = event

        val start = event.startInstant()
            .atZone(ZoneId.systemDefault())

        val end = event.endInstant()
            .atZone(ZoneId.systemDefault())

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
                val entity = EventEntity(
                    id = editedEvent?.id ?: 0L,
                    title = state.title.trim(),
                    startTime = start.toEpochMilli(),
                    endTime = end.toEpochMilli(),
                    isPublic = state.isPublic
                )

                if (editedEvent == null) {
                    dao.insert(entity)
                } else {
                    dao.update(entity)
                }

                editorState = null
                editedEvent = null
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
                dao.delete(event)
                editedEvent = null
                editorState = null
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
        val start = period.start.toEpochMilli()
        val end = period.end.toEpochMilli()

        val rows = if (publicOnly) {
            dao.getPublicForPeriod(start, end)
        } else {
            dao.getForPeriod(start, end)
        }

        return rows.mapNotNull { event ->
            val intervalStart = maxOf(
                event.startInstant(),
                period.start
            )

            val intervalEnd = minOf(
                event.endInstant(),
                period.end
            )

            if (intervalEnd.isAfter(intervalStart)) {
                TimeInterval(intervalStart, intervalEnd)
            } else {
                null
            }
        }
    }

    fun selectTab(calendar: CalendarTab) {}
}
