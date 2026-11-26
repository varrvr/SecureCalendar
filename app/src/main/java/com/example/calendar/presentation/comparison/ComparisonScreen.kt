
package com.example.calendar.presentation.comparison

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.PersonOutline
import com.example.calendar.presentation.components.AdaptiveFieldRow
import com.example.calendar.presentation.components.DateField
import com.example.calendar.presentation.components.russianLoginKeyboard
import androidx.compose.ui.res.stringResource
import com.example.calendar.R
import androidx.compose.ui.unit.dp
import com.example.calendar.domain.model.TimeInterval
import com.example.calendar.domain.model.CalendarEvent
import com.example.calendar.util.ruLocale
import java.time.Instant
import java.time.LocalDate
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
    modifier: Modifier = Modifier,
    events: List<CalendarEvent> = emptyList()
) {
    val locked = state.isSubmitting || state.status == ComparisonStatus.WAITING || state.status == ComparisonStatus.PROCESSING
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", ruLocale)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.comparison_title), style = MaterialTheme.typography.headlineMedium)
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp)) {
            Text(stringResource(if (state.isConnected) R.string.comparison_connected else R.string.comparison_disconnected),
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("С кем сравнить", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(value = state.selectedUser.orEmpty(), onValueChange = onUserSelected,
                    keyboardOptions = russianLoginKeyboard,
                    label = { Text(stringResource(R.string.comparison_username)) },
                    leadingIcon = { Icon(Icons.Outlined.PersonOutline, null) },
                    singleLine = true, enabled = !locked, modifier = Modifier.fillMaxWidth())
                FilledTonalButton(onClick = onSearchClick,
                    enabled = state.isConnected && !state.isSearching && !locked && !state.selectedUser.isNullOrBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    if (state.isSearching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else {
                        Icon(Icons.Outlined.Search, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.comparison_find))
                    }
                }
                state.searchResults.forEach { peer ->
                    Surface(onClick = { onUserSelected(peer.username) }, enabled = !locked,
                        shape = RoundedCornerShape(12.dp), color = if (state.selectedUserId == peer.id)
                            MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(peer.displayName, style = MaterialTheme.typography.titleSmall)
                            Text("@${peer.username}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("За какой период", style = MaterialTheme.typography.titleMedium)
                AdaptiveFieldRow(first = {
                    DateField(stringResource(R.string.comparison_period_start), state.periodStart, onPeriodStartChanged, it, !locked)
                }, second = {
                    DateField(stringResource(R.string.comparison_period_end), state.periodEnd, onPeriodEndChanged, it, !locked)
                })
                if (state.periodEnd.isBefore(state.periodStart))
                    Text(stringResource(R.string.comparison_invalid_period), color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.comparison_semantics), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Button(onClick = onRequestClick, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            enabled = state.isConnected && state.selectedUserId != null && !locked && !state.periodEnd.isBefore(state.periodStart)) {
            if (state.isSubmitting) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.comparison_send))
        }
        if (state.incomingRequests.isNotEmpty()) Text(stringResource(R.string.comparison_inbox), style = MaterialTheme.typography.titleMedium)
        state.incomingRequests.forEach { invite ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.comparison_invite_from, invite.username))
                    if (invite.periodStart != null && invite.periodEnd != null) {
                        val start = Instant.ofEpochSecond(invite.periodStart).atZone(comparisonZone).toLocalDate()
                        val end = Instant.ofEpochSecond(invite.periodEnd).minusNanos(1).atZone(comparisonZone).toLocalDate()
                        Text(stringResource(R.string.comparison_period, start.format(formatter), end.format(formatter)))
                    }
                    AdaptiveFieldRow(first = {
                        Button(onClick = { onAcceptRequest(invite.sessionId) }, enabled = !locked, modifier = it.heightIn(min = 48.dp)) { Text(stringResource(R.string.comparison_accept)) }
                    }, second = {
                        OutlinedButton(onClick = { onRejectRequest(invite.sessionId) }, enabled = !locked, modifier = it.heightIn(min = 48.dp)) { Text(stringResource(R.string.comparison_reject)) }
                    })
                }
            }
        }
        when (state.status) {
            ComparisonStatus.INITIAL -> Unit
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
                OutlinedButton(onClick = onRetryClick, enabled = !state.isSubmitting) { Text(stringResource(R.string.comparison_retry)) }
            }
            ComparisonStatus.SUCCESS, ComparisonStatus.EMPTY -> {
                Text(stringResource(if (state.status == ComparisonStatus.EMPTY) R.string.comparison_result_empty else R.string.comparison_result_found))
                namedComparisonResults(state.intersections, events).forEach { result ->
                    Card(modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.comparison_result_interval,
                                formatComparisonTime(result.interval.start), formatComparisonTime(result.interval.end)),
                                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            if (result.meetingTitles.isNotEmpty()) {
                                Text("Ваши встречи", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                result.meetingTitles.forEach { title ->
                                    Text(title, style = MaterialTheme.typography.bodyMedium)
                                }
                            } else {
                                Text("Название встречи недоступно в текущем календаре",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        state.errorRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        if (state.errorRes == null) state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
