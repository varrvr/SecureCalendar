package com.example.calendar.presentation.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.calendar.presentation.components.AdaptiveFieldRow
import com.example.calendar.presentation.components.DateField
import com.example.calendar.presentation.components.TimeField
import com.example.calendar.presentation.components.russianTextKeyboard
import com.example.calendar.presentation.components.openKeyboardLanguageSettings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.outlined.Keyboard
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

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
    var deleteConfirmation by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val enabled = !uiState.isSaving
    val duration = Duration.between(uiState.startDate.atTime(uiState.startTime), uiState.endDate.atTime(uiState.endTime))
    Column(modifier.fillMaxSize().imePadding()) {
        TopAppBar(title = { Text(if (uiState.isEditing) "Встреча" else "Новая встреча") },
            navigationIcon = { IconButton(onClick = onBackClick, enabled = enabled) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
            } }, actions = {
                IconButton(onClick = { openKeyboardLanguageSettings(context) }) {
                    Icon(Icons.Outlined.Keyboard, "Настроить русский и английский ввод")
                }
            })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            OutlinedTextField(value = uiState.title, onValueChange = onTitleChange,
                label = { Text("Название встречи") }, placeholder = { Text("Например, кофе с друзьями") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled,
                isError = uiState.titleError != null,
                supportingText = uiState.titleError?.let { error -> { Text(error) } },
                keyboardOptions = russianTextKeyboard.copy(imeAction = ImeAction.Done))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Начало", style = MaterialTheme.typography.titleMedium)
                AdaptiveFieldRow(first = {
                    DateField("Дата начала", uiState.startDate, onStartDateChange, it, enabled)
                }, second = {
                    TimeField("Время начала", uiState.startTime, onStartTimeChange, it, enabled)
                })
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Окончание", style = MaterialTheme.typography.titleMedium)
                AdaptiveFieldRow(first = {
                    DateField("Дата окончания", uiState.endDate, onEndDateChange, it, enabled)
                }, second = {
                    TimeField("Время окончания", uiState.endTime, onEndTimeChange, it, enabled)
                })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30L to "30 мин", 60L to "1 час", 120L to "2 часа").forEach { (minutes, label) ->
                        FilterChip(selected = duration == Duration.ofMinutes(minutes), enabled = enabled,
                            onClick = {
                                val end = uiState.startDate.atTime(uiState.startTime).plusMinutes(minutes)
                                onEndDateChange(end.toLocalDate())
                                onEndTimeChange(end.toLocalTime())
                            }, label = { Text(label) })
                    }
                }
                uiState.intervalError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Учитывать при сравнении", style = MaterialTheme.typography.titleSmall)
                    Text("Только занятое время. Название встречи остаётся у вас.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = uiState.isPublic, onCheckedChange = onPublicChange, enabled = enabled)
            }
            uiState.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (uiState.isEditing) TextButton(onClick = { deleteConfirmation = true }, enabled = enabled,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Удалить") }
                Button(onClick = onSaveClick, enabled = enabled, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                    if (uiState.isSaving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    else Text("Сохранить встречу")
                }
            }
        }
    }
    if (deleteConfirmation) AlertDialog(onDismissRequest = { deleteConfirmation = false },
        title = { Text("Удалить встречу?") }, text = { Text("Восстановить удалённую встречу будет невозможно.") },
        confirmButton = { TextButton(onClick = { deleteConfirmation = false; onDeleteClick() }) {
            Text("Удалить", color = MaterialTheme.colorScheme.error)
        } }, dismissButton = { TextButton(onClick = { deleteConfirmation = false }) { Text("Отмена") } })
}
