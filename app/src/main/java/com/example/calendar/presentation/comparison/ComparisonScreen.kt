
package com.example.calendar.presentation.comparison

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.calendar.R
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
    val errorRes: Int? = null,
    val searchResults: List<ComparisonUserUi> = emptyList(),
    val selectedUserId: Long? = null,
    val incomingRequests: List<IncomingComparisonUi> = emptyList(),
    val currentSessionId: String? = null,
    val isSearching: Boolean = false,
    val isConnected: Boolean = false,
    val isSubmitting: Boolean = false
)

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
    var editingStart by remember { mutableStateOf(true) }
    val locked = state.isSubmitting || state.status == ComparisonStatus.WAITING || state.status == ComparisonStatus.PROCESSING
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", ruLocale)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.comparison_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(if (state.isConnected) R.string.comparison_connected else R.string.comparison_disconnected))
        Text(stringResource(R.string.comparison_semantics), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = state.selectedUser.orEmpty(), onValueChange = onUserSelected,
            label = { Text(stringResource(R.string.comparison_username)) },
            singleLine = true, enabled = !locked, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = onSearchClick,
            enabled = state.isConnected && !state.isSearching && !locked && !state.selectedUser.isNullOrBlank(),
            modifier = Modifier.fillMaxWidth()) {
            if (state.isSearching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(stringResource(R.string.comparison_find))
        }
        state.searchResults.forEach { peer ->
            OutlinedButton(onClick = { onUserSelected(peer.username) }, enabled = !locked,
                modifier = Modifier.fillMaxWidth()) { Text("${peer.displayName} (@${peer.username})") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = editingStart, enabled = !locked, onClick = { editingStart = true },
                label = { Text(stringResource(R.string.comparison_period_start)) })
            FilterChip(selected = !editingStart, enabled = !locked, onClick = { editingStart = false },
                label = { Text(stringResource(R.string.comparison_period_end)) })
        }
        Text(stringResource(R.string.comparison_period, state.periodStart.format(formatter), state.periodEnd.format(formatter)))
        key(editingStart) {
            ComparisonCasino(date = if (editingStart) state.periodStart else state.periodEnd,
                enabled = !locked, onDateSelected = { date ->
                    if (editingStart) onPeriodStartChanged(date) else onPeriodEndChanged(date)
                })
        }
        if (state.periodEnd.isBefore(state.periodStart)) Text(stringResource(R.string.comparison_invalid_period), color = MaterialTheme.colorScheme.error)
        Button(onClick = onRequestClick, modifier = Modifier.fillMaxWidth(),
            enabled = state.isConnected && state.selectedUserId != null && !locked && !state.periodEnd.isBefore(state.periodStart)) {
            Text(stringResource(R.string.comparison_send))
        }
        if (state.incomingRequests.isNotEmpty()) Text(stringResource(R.string.comparison_inbox), style = MaterialTheme.typography.titleMedium)
        state.incomingRequests.forEach { invite ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.comparison_invite_from, invite.username))
                    if (invite.periodStart != null && invite.periodEnd != null) {
                        val start = Instant.ofEpochSecond(invite.periodStart).atZone(ZoneId.of("UTC")).toLocalDate()
                        val end = Instant.ofEpochSecond(invite.periodEnd).minusNanos(1).atZone(ZoneId.of("UTC")).toLocalDate()
                        Text(stringResource(R.string.comparison_period, start.format(formatter), end.format(formatter)))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onAcceptRequest(invite.sessionId) }, enabled = !locked) { Text(stringResource(R.string.comparison_accept)) }
                        OutlinedButton(onClick = { onRejectRequest(invite.sessionId) }, enabled = !locked) { Text(stringResource(R.string.comparison_reject)) }
                    }
                }
            }
        }
        when (state.status) {
            ComparisonStatus.INITIAL -> Text(stringResource(R.string.comparison_initial))
            ComparisonStatus.WAITING -> {
                Text(stringResource(R.string.comparison_waiting))
                OutlinedButton(onClick = onCancelRequest, enabled = !state.isSubmitting) { Text(stringResource(R.string.comparison_cancel)) }
            }
            ComparisonStatus.PROCESSING -> {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stringResource(R.string.comparison_processing))
                OutlinedButton(onClick = onCancelRequest) { Text(stringResource(R.string.comparison_cancel)) }
            }
            ComparisonStatus.REJECTED -> Text(stringResource(R.string.comparison_rejected))
            ComparisonStatus.ERROR -> {
                if (state.errorRes == R.string.comparison_crypto_unavailable) com.example.calendar.presentation.components.AnimatedBee()
                OutlinedButton(onClick = onRetryClick, enabled = !state.isSubmitting) { Text(stringResource(R.string.comparison_retry)) }
            }
            ComparisonStatus.SUCCESS, ComparisonStatus.EMPTY -> {
                Text(stringResource(if (state.status == ComparisonStatus.EMPTY) R.string.comparison_result_empty else R.string.comparison_result_found))
                state.intersections.forEach { interval ->
                    val format = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", ruLocale).withZone(ZoneId.of("UTC"))
                    Text(stringResource(R.string.comparison_result_interval, format.format(interval.start), format.format(interval.end)))
                }
            }
        }
        state.errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
    }
}
