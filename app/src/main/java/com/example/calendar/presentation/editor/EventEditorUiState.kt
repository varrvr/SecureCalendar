
package com.example.calendar.presentation.editor

import java.time.LocalDate
import java.time.LocalTime

data class EventEditorUiState(
    val isEditing: Boolean = false,
    val title: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val startTime: LocalTime = LocalTime.of(9, 0),
    val endDate: LocalDate = LocalDate.now(),
    val endTime: LocalTime = LocalTime.of(10, 0),
    val isPublic: Boolean = false,
    val titleError: String? = null,
    val intervalError: String? = null,
    val saveError: String? = null,
    val isSaving: Boolean = false
)
