package com.example.calendar.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calendar.domain.repository.CryptoKeyRepository
import com.example.calendar.data.repository.ServerAuthRepositoryImpl
import com.example.calendar.data.remote.auth.ServerSessionManager
import com.example.calendar.data.remote.websocket.WebSocketTransport
import com.example.calendar.data.remote.websocket.WebSocketState
import com.example.calendar.domain.model.ServerRegistrationStatus
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val cryptoKeys: CryptoKeyRepository,
    private val serverAuth: ServerAuthRepositoryImpl,
    private val serverSession: ServerSessionManager,
    private val webSocket: WebSocketTransport
) : ViewModel() {
    private val _keyState = MutableStateFlow(ProfileKeyUiState())
    val keyState = _keyState.asStateFlow()
    private var observeJob: Job? = null
    private val _serverState = MutableStateFlow(ProfileServerUiState())
    val serverState = _serverState.asStateFlow()
    private var serverObserveJob: Job? = null

    fun selectAccount(ownerId: Long) {
        if (_keyState.value.ownerId == ownerId) return
        observeJob?.cancel()
        _keyState.value = ProfileKeyUiState(ownerId = ownerId)
        observeJob = viewModelScope.launch {
            cryptoKeys.observeFingerprint(ownerId).collectLatest { fingerprint ->
                _keyState.value = _keyState.value.copy(fingerprint = fingerprint)
            }
        }
    }

    fun clearAccount() {
        observeJob?.cancel()
        observeJob = null
        serverObserveJob?.cancel()
        serverObserveJob = null
        _keyState.value = ProfileKeyUiState()
        _serverState.value = ProfileServerUiState()
    }

    fun selectAccount(ownerId: Long, status: ServerRegistrationStatus) {
        selectAccount(ownerId)
        _serverState.value = ProfileServerUiState(
            ownerId = ownerId,
            registrationStatus = status,
            authenticated = serverSession.ownerIdOrNull() == ownerId,
            connected = serverSession.ownerIdOrNull() == ownerId && webSocket.state.value == WebSocketState.CONNECTED
        )
        serverObserveJob?.cancel()
        serverObserveJob = viewModelScope.launch {
            webSocket.state.collect { state ->
                if (_serverState.value.ownerId == ownerId) {
                    _serverState.value = _serverState.value.copy(connected = state == WebSocketState.CONNECTED)
                }
            }
        }
    }

    fun connectToServer(
        ownerId: Long,
        username: String,
        displayName: String,
        password: String
    ) {
        if (_serverState.value.isLoading) return
        viewModelScope.launch {
            _serverState.value = _serverState.value.copy(ownerId = ownerId, isLoading = true, error = null)
            var publicKey: ByteArray? = null
            try {
                publicKey = cryptoKeys.publicKey(ownerId)
                    ?: error("Сначала создайте ключ Bee2: серверная регистрация требует публичный ключ")
                val encodedPublicKey = Base64.encodeToString(publicKey, Base64.NO_WRAP)
                serverAuth.connectOrRegister(
                    username = username,
                    displayName = displayName,
                    password = password,
                    publicKey = encodedPublicKey,
                    localOwnerId = ownerId,
                    registrationStatus = _serverState.value.registrationStatus
                )
                if (_serverState.value.ownerId == ownerId) {
                    _serverState.value = _serverState.value.copy(
                        isLoading = false,
                        authenticated = true,
                        registrationStatus = ServerRegistrationStatus.REGISTERED,
                        error = null
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (_serverState.value.ownerId == ownerId) {
                    _serverState.value = _serverState.value.copy(
                        isLoading = false,
                        authenticated = false,
                        error = failure.message?.take(180) ?: "Не удалось подключиться к серверу"
                    )
                }
            } finally {
                publicKey?.fill(0)
            }
        }
    }

    fun createKey() {
        val ownerId = _keyState.value.ownerId ?: return
        if (_keyState.value.isCreating) return
        viewModelScope.launch {
            if (_keyState.value.ownerId != ownerId) return@launch
            _keyState.value = _keyState.value.copy(isCreating = true, error = null)
            try {
                cryptoKeys.createKey(ownerId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (_keyState.value.ownerId == ownerId) {
                    _keyState.value = _keyState.value.copy(error = failure.message ?: "Не удалось создать ключ Bee2")
                }
            } catch (unsupported: LinkageError) {
                if (_keyState.value.ownerId == ownerId) {
                    _keyState.value = _keyState.value.copy(error = "Bee2 недоступна для архитектуры этого устройства")
                }
            } finally {
                if (_keyState.value.ownerId == ownerId) {
                    _keyState.value = _keyState.value.copy(isCreating = false)
                }
            }
        }
    }
}

data class ProfileKeyUiState(
    val ownerId: Long? = null,
    val fingerprint: String? = null,
    val isCreating: Boolean = false,
    val error: String? = null
)

data class ProfileServerUiState(
    val ownerId: Long? = null,
    val registrationStatus: ServerRegistrationStatus = ServerRegistrationStatus.NOT_REGISTERED,
    val authenticated: Boolean = false,
    val connected: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)
