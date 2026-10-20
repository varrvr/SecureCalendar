package com.example.calendar.presentation.comparison

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calendar.domain.repository.ComparisonRepository
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ComparisonViewModel(private val repository: ComparisonRepository) : ViewModel() {
    private val _state = MutableStateFlow(ComparisonUiState())
    val state = _state.asStateFlow()
    private var accountId: Long? = null
    private var incomingJob: Job? = null
    private var requestTimeoutJob: Job? = null

    init {
        viewModelScope.launch {
            repository.connected.collect { connected ->
                _state.value = _state.value.copy(isConnected = connected)
                if (connected) refreshIncoming()
                else if (_state.value.status == ComparisonStatus.WAITING) {
                    requestTimeoutJob?.cancel()
                    _state.value = _state.value.copy(status = ComparisonStatus.ERROR, error = "Соединение с сервером потеряно")
                }
            }
        }
        viewModelScope.launch {
            repository.signals.collect { signal ->
                when (signal.type) {
                    "session_request", "session_cancelled" -> refreshIncoming()
                    "session_accepted" -> if (signal.sessionId == _state.value.currentSessionId) {
                        requestTimeoutJob?.cancel()
                        _state.value = _state.value.copy(
                            status = ComparisonStatus.ERROR,
                            error = "Запрос принят. Защищённое сравнение пока недоступно: Bee2 PSI протокол ещё не реализован. Расписание не отправлялось."
                        )
                    }
                    "session_rejected" -> if (signal.sessionId == _state.value.currentSessionId) {
                        requestTimeoutJob?.cancel()
                        _state.value = _state.value.copy(status = ComparisonStatus.REJECTED, error = null)
                    }
                }
            }
        }
    }

    fun selectAccount(id: Long?) {
        if (accountId == id) return
        accountId = id
        incomingJob?.cancel()
        requestTimeoutJob?.cancel()
        _state.value = ComparisonUiState(isConnected = _state.value.isConnected)
        if (id != null) refreshIncoming()
    }

    fun selectUser(username: String) {
        val selected = _state.value.searchResults.firstOrNull { it.username.equals(username.trim(), true) }
        _state.value = _state.value.copy(
            selectedUser = username,
            selectedUserId = selected?.id,
            searchResults = if (selected == null) _state.value.searchResults else emptyList(),
            status = ComparisonStatus.INITIAL,
            error = null
        )
    }

    fun setPeriodStart(date: LocalDate) { _state.value = _state.value.copy(periodStart = date, error = null) }
    fun setPeriodEnd(date: LocalDate) { _state.value = _state.value.copy(periodEnd = date, error = null) }

    fun searchUsers() {
        val query = _state.value.selectedUser?.trim().orEmpty()
        if (query.isBlank() || !_state.value.isConnected || _state.value.isSearching) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSearching = true, error = null, searchResults = emptyList(), selectedUserId = null)
            try {
                val results = repository.searchUsers(query)
                _state.value = _state.value.copy(
                    isSearching = false,
                    searchResults = results.map { ComparisonUserUi(it.id, it.username, it.displayName) },
                    error = if (results.isEmpty()) "Пользователь не найден" else null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.value = _state.value.copy(isSearching = false, error = failure.message ?: "Поиск не выполнен")
            }
        }
    }

    fun requestComparison() {
        val current = _state.value
        val username = current.selectedUser?.trim().orEmpty()
        if (username.isBlank() || current.selectedUserId == null || !current.isConnected) return
        if (current.periodEnd.isBefore(current.periodStart)) {
            _state.value = current.copy(status = ComparisonStatus.ERROR, error = "Некорректный период")
            return
        }
        viewModelScope.launch {
            _state.value = current.copy(isSubmitting = true, error = null)
            try {
                val start = current.periodStart.atStartOfDay(ZoneOffset.UTC).toEpochSecond()
                val end = current.periodEnd.plusDays(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
                val invite = repository.createRequest(username, start, end)
                _state.value = _state.value.copy(
                    selectedUserId = invite.responderId,
                    currentSessionId = invite.sessionId,
                    isSubmitting = false,
                    status = ComparisonStatus.WAITING,
                    error = null
                )
                requestTimeoutJob?.cancel()
                requestTimeoutJob = viewModelScope.launch {
                    delay(120_000)
                    if (_state.value.currentSessionId == invite.sessionId && _state.value.status == ComparisonStatus.WAITING) {
                        runCatching { repository.cancel(invite.sessionId) }
                        _state.value = _state.value.copy(
                            status = ComparisonStatus.ERROR,
                            error = "Время ожидания ответа истекло"
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.value = _state.value.copy(isSubmitting = false, status = ComparisonStatus.ERROR, error = failure.message ?: "Не удалось отправить запрос")
            }
        }
    }

    fun refreshIncoming() {
        if (!_state.value.isConnected || accountId == null || incomingJob?.isActive == true) return
        incomingJob = viewModelScope.launch {
            try {
                val requests = repository.incomingRequests().map {
                    IncomingComparisonUi(it.sessionId, it.initiatorUsername ?: "ID ${it.initiatorId}", it.periodStartEpochSeconds, it.periodEndEpochSeconds)
                }
                _state.value = _state.value.copy(incomingRequests = requests)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The screen keeps its current state; reconnect can trigger another refresh.
            }
        }
    }

    fun acceptRequest(sessionId: String) = respond(sessionId, accept = true)
    fun rejectRequest(sessionId: String) = respond(sessionId, accept = false)

    fun cancelRequest() {
        val sessionId = _state.value.currentSessionId ?: return
        requestTimeoutJob?.cancel()
        viewModelScope.launch {
            try {
                repository.cancel(sessionId)
                _state.value = _state.value.copy(status = ComparisonStatus.INITIAL, currentSessionId = null, error = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.value = _state.value.copy(status = ComparisonStatus.ERROR, error = failure.message ?: "Не удалось отменить запрос")
            }
        }
    }

    private fun respond(sessionId: String, accept: Boolean) {
        viewModelScope.launch {
            try {
                if (accept) repository.accept(sessionId) else repository.reject(sessionId)
                _state.value = _state.value.copy(
                    incomingRequests = _state.value.incomingRequests.filterNot { it.sessionId == sessionId },
                    status = if (accept) ComparisonStatus.ERROR else _state.value.status,
                    error = if (accept) "Запрос принят, но защищённое сравнение пока недоступно: Bee2 PSI протокол ещё не реализован. Открытые интервалы не передавались." else null
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _state.value = _state.value.copy(status = ComparisonStatus.ERROR, error = failure.message ?: "Не удалось обработать запрос")
            }
        }
    }

    fun retry() {
        requestTimeoutJob?.cancel()
        _state.value = _state.value.copy(status = ComparisonStatus.INITIAL, error = null, currentSessionId = null)
        refreshIncoming()
    }
}
