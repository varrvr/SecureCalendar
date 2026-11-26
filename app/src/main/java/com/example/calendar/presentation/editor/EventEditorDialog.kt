package com.example.calendar.presentation.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.calendar.data.local.EventEntity
import startInstant
import endInstant
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Legacy entry point shares the same fields, validation and deletion confirmation as the main editor. */
@Composable
fun EventEditorDialog(existingEvent: EventEntity?, selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (EventEntity?, String, Instant, Instant, Boolean) -> Unit,
    onDelete: (EventEntity) -> Unit) {
    val zone = ZoneId.systemDefault()
    var state by remember(existingEvent, selectedDate) {
        val start = existingEvent?.startInstant()?.atZone(zone)
        val end = existingEvent?.endInstant()?.atZone(zone)
        mutableStateOf(EventEditorUiState(
            isEditing = existingEvent != null, title = existingEvent?.title.orEmpty(),
            startDate = start?.toLocalDate() ?: selectedDate, startTime = start?.toLocalTime() ?: LocalTime.of(9, 0),
            endDate = end?.toLocalDate() ?: selectedDate, endTime = end?.toLocalTime() ?: LocalTime.of(10, 0),
            isPublic = existingEvent?.isPublic ?: false))
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            EventEditorScreen(state,
                onTitleChange = { state = state.copy(title = it, titleError = null) },
                onStartDateChange = { state = state.copy(startDate = it, intervalError = null) },
                onStartTimeChange = { state = state.copy(startTime = it, intervalError = null) },
                onEndDateChange = { state = state.copy(endDate = it, intervalError = null) },
                onEndTimeChange = { state = state.copy(endTime = it, intervalError = null) },
                onPublicChange = { state = state.copy(isPublic = it) },
                onSaveClick = {
                    val start = state.startDate.atTime(state.startTime).atZone(zone).toInstant()
                    val end = state.endDate.atTime(state.endTime).atZone(zone).toInstant()
                    state = state.copy(titleError = if (state.title.isBlank()) "Введите название встречи" else null,
                        intervalError = if (!end.isAfter(start)) "Окончание должно быть позже начала" else null)
                    if (state.titleError == null && state.intervalError == null)
                        onSave(existingEvent, state.title.trim(), start, end, state.isPublic)
                }, onDeleteClick = { existingEvent?.let(onDelete) }, onBackClick = onDismiss)
        }
    }
}
