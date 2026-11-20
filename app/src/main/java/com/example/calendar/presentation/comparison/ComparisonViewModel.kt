package com.example.calendar.presentation.comparison

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calendar.R
import com.example.calendar.crypto.psi.PsiUnavailableException
import com.example.calendar.domain.repository.ComparisonRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.ZoneOffset

/** REST is the durable source of truth; WebSocket events trigger immediate reconciliation. */
class ComparisonViewModel(private val repository: ComparisonRepository) : ViewModel() {
    private val _state = MutableStateFlow(ComparisonUiState())
    val state = _state.asStateFlow()
    private var accountId: Long? = null
    private var generation = 0L
    private var operation: Job? = null
    private var refresh: Job? = null
    private var timeout: Job? = null
    init {
        viewModelScope.launch {
            repository.connected.collect { connected ->
                _state.update { it.copy(isConnected = connected) }
                if (connected) refreshIncoming()
                else if (_state.value.status == ComparisonStatus.WAITING) {
                    _state.update { it.copy(errorRes = R.string.comparison_connection_lost) }
                }
            }
        }
        viewModelScope.launch {
            repository.signals.collect { signal ->
                if (signal.type == "peer_offline" && signal.sessionId == _state.value.currentSessionId) {
                    _state.update { it.copy(errorRes = R.string.comparison_peer_offline) }
                }
                refreshIncoming()
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(5000)
                refreshIncoming()
            }
        }
    }
    fun selectAccount(id: Long?) {
        if (id == accountId) return
        generation++; accountId = id
        operation?.cancel(); refresh?.cancel(); timeout?.cancel()
        _state.value = ComparisonUiState(isConnected = false)
        refreshIncoming()
    }
    private fun busy() = operation?.isActive == true || _state.value.status in
        setOf(ComparisonStatus.WAITING, ComparisonStatus.PROCESSING)
    fun selectUser(username: String) {
        if (busy()) return
        val peer = _state.value.searchResults.firstOrNull { it.username == username.trim().lowercase() }
        _state.update { it.copy(selectedUser = username, selectedUserId = peer?.id,
            status = ComparisonStatus.INITIAL, errorRes = null) }
    }
    fun setPeriodStart(date: LocalDate) { if (!busy()) _state.update { it.copy(periodStart = date, errorRes = null) } }
    fun setPeriodEnd(date: LocalDate) { if (!busy()) _state.update { it.copy(periodEnd = date, errorRes = null) } }
    fun searchUsers() {
        val query = _state.value.selectedUser?.trim().orEmpty()
        if (busy() || query.isBlank() || !_state.value.isConnected) return
        val version = generation
        operation = viewModelScope.launch {
            _state.update { it.copy(isSearching = true, errorRes = null, searchResults = emptyList(), selectedUserId = null) }
            try {
                val peers = repository.searchUsers(query)
                if (version != generation) return@launch
                _state.update { it.copy(searchResults = peers.map { p -> ComparisonUserUi(p.id, p.username, p.displayName) },
                    selectedUserId = peers.firstOrNull { p -> p.username == query.lowercase() }?.id,
                    errorRes = if (peers.isEmpty()) R.string.comparison_user_not_found else null) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (version == generation) _state.update { it.copy(errorRes = errorFor(failure)) } }
            finally { if (version == generation) _state.update { it.copy(isSearching = false) } }
        }
    }
    fun requestComparison() {
        val current = _state.value
        if (busy() || current.selectedUserId == null || !current.isConnected) return
        if (current.periodEnd.isBefore(current.periodStart)) {
            _state.update { it.copy(errorRes = R.string.comparison_invalid_period) }; return
        }
        val version = generation
        _state.update { it.copy(isSubmitting = true, errorRes = null) }
        operation = viewModelScope.launch {
            try {
                val invite = repository.createRequest(current.selectedUser.orEmpty().trim().lowercase(),
                    current.periodStart.atStartOfDay(ZoneOffset.UTC).toEpochSecond(),
                    current.periodEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond())
                if (version != generation) return@launch
                _state.update { it.copy(currentSessionId = invite.sessionId, status = ComparisonStatus.WAITING) }
                timeout?.cancel()
                timeout = viewModelScope.launch {
                    delay(120000)
                    if (version == generation && _state.value.currentSessionId == invite.sessionId &&
                        _state.value.status == ComparisonStatus.WAITING) {
                        try { repository.cancel(invite.sessionId) } catch (c: CancellationException) { throw c } catch (_: Exception) { }
                        _state.update { it.copy(status = ComparisonStatus.ERROR, errorRes = R.string.comparison_timeout) }
                    }
                }
                refreshIncoming()
            } catch (c: CancellationException) { throw c }
            catch (failure: Exception) { if (version == generation) _state.update { it.copy(status = ComparisonStatus.ERROR, errorRes = errorFor(failure)) } }
            finally { if (version == generation) _state.update { it.copy(isSubmitting = false) } }
        }
    }
    fun refreshIncoming() {
        if (accountId == null || refresh?.isActive == true) return
        val version = generation
        refresh = viewModelScope.launch {
            try {
                val inbox = repository.incomingRequests()
                if (version != generation) return@launch
                _state.update { it.copy(incomingRequests = inbox.map { r ->
                    IncomingComparisonUi(r.sessionId, r.initiatorUsername.orEmpty(), r.periodStartEpochSeconds, r.periodEndEpochSeconds)
                }) }
                val id = _state.value.currentSessionId
                if (id != null && _state.value.status == ComparisonStatus.WAITING) {
                    when (repository.session(id).status) {
                        "accepted", "active" -> prepare(id, version)
                        "rejected" -> { timeout?.cancel(); _state.update { it.copy(status = ComparisonStatus.REJECTED, errorRes = null) } }
                        "cancelled" -> { timeout?.cancel(); _state.update { it.copy(status = ComparisonStatus.ERROR, errorRes = R.string.comparison_cancelled) } }
                    }
                }
            } catch (c: CancellationException) { throw c }
            catch (_: Exception) { /* Durable reconciliation retries after reconnect; no success is synthesized. */ }
        }
    }
    private suspend fun prepare(id: String, version: Long) {
        timeout?.cancel()
        if (version != generation) return
        _state.update { it.copy(status = ComparisonStatus.PROCESSING, errorRes = null) }
        try {
            repository.preparePsi(id)
            // A future engine must return a verified result through a separate result contract.
            _state.update { it.copy(status = ComparisonStatus.ERROR, errorRes = R.string.comparison_crypto_unavailable) }
        } catch (c: CancellationException) { throw c }
        catch (failure: Exception) {
            if (version == generation) _state.update { it.copy(status = ComparisonStatus.ERROR,
                errorRes = if (failure is PsiUnavailableException) R.string.comparison_crypto_unavailable else errorFor(failure)) }
        }
    }
    fun acceptRequest(id: String) = respond(id, true)
    fun rejectRequest(id: String) = respond(id, false)
    private fun respond(id: String, accept: Boolean) {
        if (operation?.isActive == true) return
        val version = generation
        _state.update { it.copy(isSubmitting = true) }
        operation = viewModelScope.launch {
            try {
                if (accept) repository.accept(id) else repository.reject(id)
                if (version != generation) return@launch
                _state.update { it.copy(incomingRequests = it.incomingRequests.filterNot { r -> r.sessionId == id },
                    currentSessionId = if (accept) id else it.currentSessionId) }
                if (accept) prepare(id, version)
            } catch (c: CancellationException) { throw c }
            catch (failure: Exception) { if (version == generation) _state.update { it.copy(errorRes = errorFor(failure), status = ComparisonStatus.ERROR) } }
            finally { if (version == generation) _state.update { it.copy(isSubmitting = false) } }
        }
    }
    fun cancelRequest() {
        val id = _state.value.currentSessionId ?: return
        if (operation?.isActive == true) return
        val version = generation
        operation = viewModelScope.launch {
            try {
                val status = repository.session(id).status
                if (status in setOf("pending", "accepted", "active")) repository.cancel(id)
                timeout?.cancel()
                if (version == generation) _state.update { it.copy(status = ComparisonStatus.INITIAL, currentSessionId = null, errorRes = null) }
            } catch (c: CancellationException) { throw c }
            catch (failure: Exception) { if (version == generation) _state.update { it.copy(errorRes = errorFor(failure)) } }
        }
    }
    fun retry() {
        if (_state.value.currentSessionId != null) cancelRequest()
        else { _state.update { it.copy(status = ComparisonStatus.INITIAL, errorRes = null) }; refreshIncoming() }
    }
    private fun errorFor(failure: Exception): Int = when (failure) {
        is com.example.calendar.data.remote.comparison.ComparisonApiException -> when (failure.code) {
            401 -> R.string.comparison_auth_required
            404 -> R.string.comparison_user_not_found
            409 -> R.string.comparison_conflict
            else -> R.string.comparison_request_failed
        }
        is java.io.IOException -> R.string.comparison_connection_lost
        else -> R.string.comparison_request_failed
    }
}
