package com.example.calendar.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calendar.domain.repository.CryptoKeyRepository
import com.example.calendar.data.repository.ServerAuthRepositoryImpl
import com.example.calendar.data.remote.auth.ServerSessionManager
import com.example.calendar.data.remote.websocket.WebSocketTransport
import com.example.calendar.data.remote.websocket.WebSocketState
import com.example.calendar.data.remote.ServerEndpointSettings
import com.example.calendar.domain.model.ServerRegistrationStatus
import android.util.Base64
import android.util.Log
import android.os.Build
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
    private val webSocket: WebSocketTransport,
    private val serverEndpointSettings: ServerEndpointSettings
) : ViewModel() {
    private companion object {
        const val TAG = "SecureCalendar.Profile"
    }
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
            serverBaseUrl = serverEndpointSettings.current(),
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

    fun updateServerAddress(value: String) {
        try {
            val normalized = serverEndpointSettings.update(value)
            serverSession.clear()
            webSocket.disconnect()
            _serverState.value = _serverState.value.copy(
                serverBaseUrl = normalized,
                authenticated = false,
                connected = false,
                error = null
            )
            Log.i(TAG, "Server address saved; previous in-memory server session cleared")
        } catch (failure: IllegalArgumentException) {
            Log.w(TAG, "Invalid server address; ${failure.javaClass.simpleName}: ${failure.message}")
            _serverState.value = _serverState.value.copy(error = failure.message ?: "Некорректный адрес сервера")
        } catch (failure: Exception) {
            Log.e(TAG, "Could not save server address; ${failure.javaClass.simpleName}", failure)
            _serverState.value = _serverState.value.copy(error = "Не удалось сохранить адрес сервера")
        }
    }

    fun connectToServer(
        ownerId: Long,
        username: String,
        displayName: String,
        password: String
    ) {
        if (_serverState.value.isLoading) {
            Log.w(TAG, "Connect ignored: authentication is already in progress")
            return
        }
        Log.i(TAG, "Server connection requested; ownerId=$ownerId, registrationStatus=${_serverState.value.registrationStatus}")
        viewModelScope.launch {
            _serverState.value = _serverState.value.copy(ownerId = ownerId, isLoading = true, error = null)
            var publicKey: ByteArray? = null
            try {
                Log.d(TAG, "Reading this account's Bee2 public key")
                publicKey = cryptoKeys.publicKey(ownerId)
                    ?: error("Сначала создайте ключ Bee2: серверная регистрация требует публичный ключ")
                Log.d(TAG, "Public key found; bytes=${publicKey.size}; starting server OPAQUE flow")
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
                        error = null,
                    )
                }
                Log.i(TAG, "Server OPAQUE flow and session setup completed; ownerId=$ownerId")
            } catch (cancelled: CancellationException) {
                Log.i(TAG, "Server connection cancelled; ownerId=$ownerId")
                throw cancelled
            } catch (failure: Exception) {
                Log.e(TAG, "Server connection failed; ownerId=$ownerId; error=${failure.javaClass.simpleName}: ${failure.message}", failure)
                if (_serverState.value.ownerId == ownerId) {
                    _serverState.value = _serverState.value.copy(
                        isLoading = false,
                        authenticated = false,
                        error = failure.message?.take(180) ?: "Не удалось подключиться к серверу"
                    )
                }
            } catch (failure: LinkageError) {
                Log.e(TAG, "Native Bee2 linkage failed during server setup; supportedAbis=${Build.SUPPORTED_ABIS.joinToString()}; ${failure.javaClass.simpleName}: ${failure.message}", failure)
                if (_serverState.value.ownerId == ownerId) {
                    _serverState.value = _serverState.value.copy(
                        isLoading = false,
                        authenticated = false,
                        error = "Bee2 не загрузилась (${failure.javaClass.simpleName}). См. Logcat: SecureCalendar.Bee2"
                    )
                }
            } finally {
                publicKey?.fill(0)
                Log.d(TAG, "Server connection operation finished; temporary public-key copy cleared")
            }
        }
    }

    fun createKey() {
        val ownerId = _keyState.value.ownerId ?: return
        if (_keyState.value.isCreating) return
        viewModelScope.launch {
            if (_keyState.value.ownerId != ownerId) return@launch
            _keyState.value = _keyState.value.copy(isCreating = true, error = null)
            Log.i(TAG, "Bee2 key creation started; ownerId=$ownerId; supportedAbis=${Build.SUPPORTED_ABIS.joinToString()}")
            try {
                val fingerprint = cryptoKeys.createKey(ownerId)
                Log.i(TAG, "Bee2 key created and stored; ownerId=$ownerId; fingerprint=$fingerprint")
            } catch (cancelled: CancellationException) {
                Log.i(TAG, "Bee2 key creation cancelled; ownerId=$ownerId")
                throw cancelled
            } catch (failure: Exception) {
                Log.e(TAG, "Bee2 key creation failed; ownerId=$ownerId; error=${failure.javaClass.simpleName}: ${failure.message}", failure)
                if (_keyState.value.ownerId == ownerId) {
                    _keyState.value = _keyState.value.copy(error = failure.message ?: "Не удалось создать ключ Bee2")
                }
            } catch (unsupported: LinkageError) {
                Log.e(TAG, "Bee2 native library unavailable; ownerId=$ownerId; supportedAbis=${Build.SUPPORTED_ABIS.joinToString()}; ${unsupported.javaClass.simpleName}: ${unsupported.message}", unsupported)
                if (_keyState.value.ownerId == ownerId) {
                    _keyState.value = _keyState.value.copy(error = "Bee2 не загрузилась (${unsupported.javaClass.simpleName}). См. Logcat: SecureCalendar.Bee2")
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
    val serverBaseUrl: String = "",
    val registrationStatus: ServerRegistrationStatus = ServerRegistrationStatus.NOT_REGISTERED,
    val authenticated: Boolean = false,
    val connected: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)
