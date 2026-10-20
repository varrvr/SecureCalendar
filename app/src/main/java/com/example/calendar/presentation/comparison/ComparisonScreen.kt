
package com.example.calendar.presentation.comparison

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.calendar.domain.model.TimeInterval
import com.example.calendar.util.ruLocale
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ComparisonUserUi(val id: Long, val username: String, val displayName: String)
data class IncomingComparisonUi(val sessionId: String, val username: String, val periodStart: Long?, val periodEnd: Long?)

enum class ComparisonStatus {
    INITIAL,
    WAITING,
    PROCESSING,
    SUCCESS,
    EMPTY,
    REJECTED,
    ERROR
}

data class ComparisonUiState(
    val selectedUser: String? = null,
    val periodStart: LocalDate = LocalDate.now(),
    val periodEnd: LocalDate = LocalDate.now().plusDays(7),
    val status: ComparisonStatus = ComparisonStatus.INITIAL,
    val intersections: List<TimeInterval> = emptyList(),
    val error: String? = null,
    val searchResults: List<ComparisonUserUi> = emptyList(),
    val selectedUserId: Long? = null,
    val incomingRequests: List<IncomingComparisonUi> = emptyList(),
    val currentSessionId: String? = null,
    val isSearching: Boolean = false,
    val isConnected: Boolean = false,
    val isSubmitting: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonScreen(
    state: ComparisonUiState,
    onUserSelected: (String) -> Unit,
    onPeriodStartChanged: (LocalDate) -> Unit,
    onPeriodEndChanged: (LocalDate) -> Unit,
    onSearchClick: () -> Unit,
    onRequestClick: () -> Unit,
    onCancelRequest: () -> Unit,
    onAcceptRequest: (String) -> Unit,
    onRejectRequest: (String) -> Unit,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectStart by remember {
        mutableStateOf<Boolean?>(null)
    }

    val formatter = DateTimeFormatter.ofPattern(
        "dd.MM.yyyy",
        ruLocale
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Сравнение календарей",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            "Для сравнения используется только согласованный " +
                    "период и выбранный пользователь.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = state.selectedUser.orEmpty(),
            onValueChange = onUserSelected,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Логин пользователя") },
            placeholder = { Text("Введите логин") },
            singleLine = true,
            enabled = state.status != ComparisonStatus.WAITING && state.status != ComparisonStatus.PROCESSING
        )

        OutlinedButton(
            onClick = onSearchClick,
            enabled = state.isConnected && !state.isSearching && !state.selectedUser.isNullOrBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isSearching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Найти пользователя")
        }
        state.searchResults.forEach { peer ->
            OutlinedButton(
                onClick = { onUserSelected(peer.username) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("${peer.displayName} (@${peer.username})")
            }
        }

        Text(
            if (state.isConnected) "Сервер подключён" else "Подключитесь к серверу в профиле для поиска пользователей.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (state.incomingRequests.isNotEmpty()) {
            Text("Входящие запросы", style = MaterialTheme.typography.titleMedium)
            state.incomingRequests.forEach { invite ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Запрос от ${invite.username} · ${invite.sessionId.take(8)}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onAcceptRequest(invite.sessionId) }) { Text("Принять") }
                            OutlinedButton(onClick = { onRejectRequest(invite.sessionId) }) { Text("Отклонить") }
                        }
                    }
                }
            }
        }

        if (state.status == ComparisonStatus.WAITING) {
            OutlinedButton(onClick = onCancelRequest, modifier = Modifier.fillMaxWidth()) {
                Text("Отменить запрос")
            }
        }

        OutlinedButton(
            onClick = { selectStart = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Начало периода: ${state.periodStart.format(formatter)}"
            )
        }

        OutlinedButton(
            onClick = { selectStart = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Конец периода: ${state.periodEnd.format(formatter)}"
            )
        }

        if (state.periodEnd.isBefore(state.periodStart)) {
            Text(
                "Конец периода не может быть раньше начала",
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            onClick = onRequestClick,
            enabled = state.selectedUserId != null && state.isConnected &&
                    !state.periodEnd.isBefore(state.periodStart) &&
                    !state.isSubmitting && state.status != ComparisonStatus.PROCESSING &&
                    state.status != ComparisonStatus.WAITING,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isSubmitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text("Запросить сравнение")
        }

        when (state.status) {
            ComparisonStatus.INITIAL -> {
                Text("Выберите пользователя и период сравнения")
            }

            ComparisonStatus.WAITING -> {
                Text("Ожидание ответа пользователя")
            }

            ComparisonStatus.PROCESSING -> {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    "Выполняется защищённое сравнение (PSI)…"
                )
            }

            ComparisonStatus.SUCCESS -> {
                Text(
                    "Сравнение с пользователем " +
                            state.selectedUser.orEmpty(),
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "Найдено пересечений: ${state.intersections.size}"
                )

                val dayFormatter = DateTimeFormatter.ofPattern(
                    "d MMMM",
                    ruLocale
                )

                val timeFormatter = DateTimeFormatter.ofPattern(
                    "HH:mm"
                )

                state.intersections.forEach { interval ->
                    val start = interval.start.atZone(
                        ZoneId.systemDefault()
                    )

                    val end = interval.end.atZone(
                        ZoneId.systemDefault()
                    )

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            Modifier.padding(16.dp)
                        ) {
                            Text(start.format(dayFormatter))
                            Text(
                                "${start.format(timeFormatter)}–" +
                                        end.format(timeFormatter)
                            )
                        }
                    }
                }
            }

            ComparisonStatus.EMPTY -> {
                Text(
                    "За выбранный период пересечений не найдено"
                )
            }

            ComparisonStatus.REJECTED -> {
                Text(
                    "Пользователь отклонил запрос на сравнение"
                )
            }

            ComparisonStatus.ERROR -> {
                Text(
                    state.error ?: "Ошибка сравнения",
                    color = MaterialTheme.colorScheme.error
                )

                OutlinedButton(
                    onClick = onRetryClick
                ) {
                    Text("Повторить")
                }
            }
        }
    }

    if (selectStart != null) {
        val isStart = selectStart == true

        val current = if (isStart) {
            state.periodStart
        } else {
            state.periodEnd
        }

        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = current
                .atStartOfDay(ZoneId.of("UTC"))
                .toInstant()
                .toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = {
                selectStart = null
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let {
                            val date = Instant
                                .ofEpochMilli(it)
                                .atZone(ZoneId.of("UTC"))
                                .toLocalDate()

                            if (isStart) {
                                onPeriodStartChanged(date)
                            } else {
                                onPeriodEndChanged(date)
                            }
                        }

                        selectStart = null
                    }
                ) {
                    Text("Готово")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { selectStart = null }
                ) {
                    Text("Отмена")
                }
            }
        ) {
            DatePicker(state = dateState)
        }
    }
}
