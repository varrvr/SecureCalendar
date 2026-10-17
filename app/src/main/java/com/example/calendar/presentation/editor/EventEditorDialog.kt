package com.example.calendar.presentation.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDefaults.dateFormatter
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.calendar.data.local.EventEntity
import dateFormatter
import endInstant
import startInstant
import timeFormatter
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

// -------------------- EVENT EDITOR --------------------

@Composable
fun EventEditorDialog(
    existingEvent: EventEntity?,
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (
        EventEntity?,
        String,
        Instant,
        Instant,
        Boolean
    ) -> Unit,
    onDelete: (EventEntity) -> Unit
) {
    val zone = ZoneId.systemDefault()

    val existingStart = existingEvent
        ?.startInstant()
        ?.atZone(zone)

    val existingEnd = existingEvent
        ?.endInstant()
        ?.atZone(zone)

    var title by remember(existingEvent) {
        mutableStateOf(existingEvent?.title ?: "")
    }

    var startDateText by remember(existingEvent) {
        mutableStateOf(
            (existingStart?.toLocalDate() ?: selectedDate)
                .format(dateFormatter)
        )
    }

    var endDateText by remember(existingEvent) {
        mutableStateOf(
            (existingEnd?.toLocalDate() ?: selectedDate)
                .format(dateFormatter)
        )
    }

    var startTimeText by remember(existingEvent) {
        mutableStateOf(
            existingStart?.toLocalTime()
                ?.format(timeFormatter) ?: "09:00"
        )
    }

    var endTimeText by remember(existingEvent) {
        mutableStateOf(
            existingEnd?.toLocalTime()
                ?.format(timeFormatter) ?: "10:00"
        )
    }

    var isPublic by remember(existingEvent) {
        mutableStateOf(existingEvent?.isPublic ?: false)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existingEvent == null) {
                    "Новая встреча"
                } else {
                    "Редактирование встречи"
                }
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        error = null
                    },
                    label = {
                        Text("Название")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = startDateText,
                    onValueChange = {
                        startDateText = it
                        error = null
                    },
                    label = {
                        Text("Дата начала")
                    },
                    supportingText = {
                        Text("ГГГГ-ММ-ДД")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = startTimeText,
                    onValueChange = {
                        startTimeText = it
                        error = null
                    },
                    label = {
                        Text("Время начала")
                    },
                    supportingText = {
                        Text("ЧЧ:ММ")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = endDateText,
                    onValueChange = {
                        endDateText = it
                        error = null
                    },
                    label = {
                        Text("Дата окончания")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = endTimeText,
                    onValueChange = {
                        endTimeText = it
                        error = null
                    },
                    label = {
                        Text("Время окончания")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {

                    Text("Публичная встреча")

                    Switch(
                        checked = isPublic,
                        onCheckedChange = {
                            isPublic = it
                        }
                    )
                }

                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (existingEvent != null) {
                    TextButton(
                        onClick = {
                            onDelete(existingEvent)
                        }
                    ) {
                        Text(
                            text = "Удалить встречу",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    try {
                        if (title.isBlank()) {
                            error = "Введите название встречи"
                            return@TextButton
                        }

                        val startDate =
                            LocalDate.parse(startDateText, dateFormatter)

                        val endDate =
                            LocalDate.parse(endDateText, dateFormatter)

                        val startTime =
                            LocalTime.parse(startTimeText, timeFormatter)

                        val endTime =
                            LocalTime.parse(endTimeText, timeFormatter)

                        val start = startDate
                            .atTime(startTime)
                            .atZone(zone)
                            .toInstant()

                        val end = endDate
                            .atTime(endTime)
                            .atZone(zone)
                            .toInstant()

                        if (!end.isAfter(start)) {
                            error = "Окончание должно быть позже начала"
                            return@TextButton
                        }

                        onSave(
                            existingEvent,
                            title,
                            start,
                            end,
                            isPublic
                        )

                    } catch (_: DateTimeParseException) {
                        error = "Проверьте формат даты и времени"
                    } catch (_: DateTimeException) {
                        error = "Некорректные дата или время"
                    }
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

