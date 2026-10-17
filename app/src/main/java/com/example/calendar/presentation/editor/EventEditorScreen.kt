
package com.example.calendar.presentation.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.calendar.util.ruLocale
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditorScreen(
    uiState: EventEditorUiState,
    onTitleChange: (String) -> Unit,
    onStartDateChange: (LocalDate) -> Unit,
    onStartTimeChange: (LocalTime) -> Unit,
    onEndDateChange: (LocalDate) -> Unit,
    onEndTimeChange: (LocalTime) -> Unit,
    onPublicChange: (Boolean) -> Unit,
    onSaveClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var deleteConfirmation by remember {
        mutableStateOf(false)
    }

    var picker by remember {
        mutableStateOf<String?>(null)
    }

    val dateLabel = DateTimeFormatter.ofPattern(
        "dd MMMM yyyy",
        ruLocale
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        TopAppBar(
            title = {
                Text(
                    if (uiState.isEditing) {
                        "Редактирование встречи"
                    } else {
                        "Новая встреча"
                    }
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onBackClick,
                    enabled = !uiState.isSaving
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад"
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = uiState.title,
                onValueChange = onTitleChange,
                label = { Text("Название встречи") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !uiState.isSaving,
                isError = uiState.titleError != null,
                supportingText = uiState.titleError?.let { error ->
                    { Text(error) }
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done
                )
            )

            Text(
                "Начало",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !uiState.isSaving) {
                        picker = "startDate"
                    }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Дата начала",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(uiState.startDate.format(dateLabel))
                }
            }

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !uiState.isSaving) {
                        picker = "startTime"
                    }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Время начала",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        uiState.startTime.format(
                            DateTimeFormatter.ofPattern("HH:mm")
                        )
                    )
                }
            }

            Text(
                "Окончание",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !uiState.isSaving) {
                        picker = "endDate"
                    }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Дата окончания",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(uiState.endDate.format(dateLabel))
                }
            }

            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !uiState.isSaving) {
                        picker = "endTime"
                    }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Время окончания",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        uiState.endTime.format(
                            DateTimeFormatter.ofPattern("HH:mm")
                        )
                    )
                }
            }

            uiState.intervalError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Публичная встреча")
                    Text(
                        "Доступна для сравнения",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = uiState.isPublic,
                    onCheckedChange = onPublicChange,
                    enabled = !uiState.isSaving
                )
            }

            uiState.saveError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = onSaveClick,
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Сохранить")
                }
            }

            if (uiState.isEditing) {
                OutlinedButton(
                    onClick = { deleteConfirmation = true },
                    enabled = !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Удалить",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (deleteConfirmation) {
        AlertDialog(
            onDismissRequest = {
                deleteConfirmation = false
            },
            title = { Text("Удалить встречу?") },
            text = {
                Text(
                    "Восстановить удалённую встречу будет невозможно."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteConfirmation = false
                        onDeleteClick()
                    }
                ) {
                    Text(
                        "Удалить",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        deleteConfirmation = false
                    }
                ) {
                    Text("Отмена")
                }
            }
        )
    }

    if (picker == "startDate" || picker == "endDate") {
        val isStart = picker == "startDate"

        val current = if (isStart) {
            uiState.startDate
        } else {
            uiState.endDate
        }

        val state = rememberDatePickerState(
            initialSelectedDateMillis = current.toUtcPickerMillis()
        )

        DatePickerDialog(
            onDismissRequest = { picker = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            val chosen = Instant
                                .ofEpochMilli(millis)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()

                            if (isStart) {
                                onStartDateChange(chosen)
                            } else {
                                onEndDateChange(chosen)
                            }
                        }
                        picker = null
                    }
                ) {
                    Text("Готово")
                }
            },
            dismissButton = {
                TextButton(onClick = { picker = null }) {
                    Text("Отмена")
                }
            }
        ) {
            DatePicker(state = state)
        }
    }

    if (picker == "startTime" || picker == "endTime") {
        val isStart = picker == "startTime"

        val current = if (isStart) {
            uiState.startTime
        } else {
            uiState.endTime
        }

        val state = rememberTimePickerState(
            initialHour = current.hour,
            initialMinute = current.minute,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { picker = null },
            title = {
                Text(
                    if (isStart) "Время начала"
                    else "Время окончания"
                )
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val chosen = LocalTime.of(
                            state.hour,
                            state.minute
                        )

                        if (isStart) {
                            onStartTimeChange(chosen)
                        } else {
                            onEndTimeChange(chosen)
                        }

                        picker = null
                    }
                ) {
                    Text("Готово")
                }
            },
            dismissButton = {
                TextButton(onClick = { picker = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

private fun LocalDate.toUtcPickerMillis(): Long =
    atStartOfDay(ZoneId.of("UTC"))
        .toInstant()
        .toEpochMilli()
