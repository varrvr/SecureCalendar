package com.example.calendar.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.calendar.presentation.editor.MeetingTimePickerDialog
import com.example.calendar.util.ruLocale
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun DateField(label: String, value: LocalDate, onValueChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    var open by rememberSaveable { mutableStateOf(false) }
    PickerField(label, value.format(DateTimeFormatter.ofPattern("d MMM yyyy", ruLocale)),
        Icons.Outlined.CalendarMonth, { open = true }, modifier, enabled)
    if (open) DateSelectionDialog(value, label, { open = false }) {
        onValueChange(it)
        open = false
    }
}

@Composable
fun TimeField(label: String, value: LocalTime, onValueChange: (LocalTime) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    var open by rememberSaveable { mutableStateOf(false) }
    PickerField(label, value.format(DateTimeFormatter.ofPattern("HH:mm")),
        Icons.Outlined.Schedule, { open = true }, modifier, enabled)
    if (open) MeetingTimePickerDialog(value, label, { open = false }) {
        onValueChange(it)
        open = false
    }
}

@Composable
private fun PickerField(label: String, value: String, icon: ImageVector, onClick: () -> Unit,
    modifier: Modifier, enabled: Boolean) {
    Surface(onClick = onClick, enabled = enabled, modifier = modifier,
        shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Stack fields on small screens and at larger accessibility font sizes. */
@Composable
fun AdaptiveFieldRow(modifier: Modifier = Modifier,
    first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth < 320.dp || fontScale > 1.25f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                first(Modifier.fillMaxWidth())
                second(Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                first(Modifier.weight(1.25f))
                second(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateSelectionDialog(current: LocalDate, title: String,
    onDismiss: () -> Unit, onConfirm: (LocalDate) -> Unit) {
    // Material represents calendar dates at UTC midnight, independently of the device zone.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = current.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    DatePickerDialog(onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = state.selectedDateMillis != null, onClick = {
                state.selectedDateMillis?.let { onConfirm(Instant.ofEpochMilli(it).atOffset(ZoneOffset.UTC).toLocalDate()) }
            }) { Text("Выбрать") }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            DatePicker(state = state, title = {
                Text(title, Modifier.padding(start = 24.dp, top = 16.dp), style = MaterialTheme.typography.labelLarge)
            }, showModeToggle = true)
            Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { state.selectedDateMillis = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }) { Text("Сегодня") }
                TextButton(onClick = { state.selectedDateMillis = LocalDate.now().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }) { Text("Завтра") }
            }
        }
    }
}
