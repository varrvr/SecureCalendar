package com.example.calendar.data.repository

import com.example.calendar.data.crypto.OpaqueClient
import com.example.calendar.data.remote.auth.AuthApi
import com.example.calendar.data.remote.auth.ServerAuthResult
import com.example.calendar.data.remote.auth.ServerSessionManager
import com.example.calendar.data.local.auth.LocalUserDao
import com.example.calendar.domain.model.ServerRegistrationStatus
import com.example.calendar.data.remote.websocket.WebSocketTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ServerAuthRepositoryImpl(
    private val opaqueClient: OpaqueClient,
    private val authApi: AuthApi,
    private val serverSessionManager: ServerSessionManager,
    private val webSocketTransport: WebSocketTransport? = null,
    private val webSocketUrl: String? = null,
    private val localUsers: LocalUserDao? = null
) {
    private val opaqueMutex = Mutex()

    suspend fun register(
        username: String,
        displayName: String,
        password: String,
        publicKey: String,
        localOwnerId: Long
    ): ServerAuthResult = opaqueMutex.withLock { withContext(Dispatchers.IO) {

        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        var state: ByteArray? = null
        var requestMessage: ByteArray? = null
        var serverResponse: ByteArray? = null
        var upload: ByteArray? = null

        try {
            val start = opaqueClient.registrationStart(
                passwordBytes
            )
            state = start.state
            requestMessage = start.message

            val responseBytes = authApi.registrationStart(
                username = username,
                displayName = displayName,
                publicKey = publicKey,
                message = start.message
            )
            serverResponse = responseBytes

            val uploadBytes = opaqueClient.registrationFinish(
                username = username,
                password = passwordBytes,
                state = start.state,
                serverResponse = responseBytes
            )
            upload = uploadBytes

            authApi.registrationFinish(
                username = username,
                message = uploadBytes
            ).also { result -> completeSession(localOwnerId, result.accessToken) }

        } catch (e: CancellationException) {
            throw e
        } finally {
            passwordBytes.fill(0)
            state?.fill(0)
            requestMessage?.fill(0)
            serverResponse?.fill(0)
            upload?.fill(0)
        }
    } }

    suspend fun login(
        username: String,
        password: String,
        localOwnerId: Long
    ): ServerAuthResult = opaqueMutex.withLock { withContext(Dispatchers.IO) {

        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        var state: ByteArray? = null
        var requestMessage: ByteArray? = null
        var serverResponse: ByteArray? = null
        var finalMessage: ByteArray? = null

        try {
            val start = opaqueClient.loginStart(
                passwordBytes
            )
            state = start.state
            requestMessage = start.message

            val challenge = authApi.loginStart(
                username = username,
                message = requestMessage
            )
            serverResponse = challenge.credentialResponse

            val finalization = opaqueClient.loginFinish(
                username = username,
                password = passwordBytes,
                state = start.state,
                serverResponse = challenge.credentialResponse
            )
            finalMessage = finalization

            authApi.loginFinish(
                username = username,
                loginStateId = challenge.loginStateId,
                message = finalization
            ).also { result -> completeSession(localOwnerId, result.accessToken) }

        } catch (e: CancellationException) {
            throw e
        } finally {
            passwordBytes.fill(0)
            state?.fill(0)
            requestMessage?.fill(0)
            serverResponse?.fill(0)
            finalMessage?.fill(0)
        }
    } }

    suspend fun connectOrRegister(
        username: String,
        displayName: String,
        password: String,
        publicKey: String,
        localOwnerId: Long,
        registrationStatus: ServerRegistrationStatus
    ): ServerAuthResult {
        return when (registrationStatus) {
            ServerRegistrationStatus.REGISTERED -> login(username, password, localOwnerId)
            ServerRegistrationStatus.NOT_REGISTERED -> try {
                register(username, displayName, password, publicKey, localOwnerId)
            } catch (conflict: com.example.calendar.data.remote.auth.AuthApiException) {
                if (conflict.statusCode != 409) throw conflict
                login(username, password, localOwnerId)
            }
            ServerRegistrationStatus.UNKNOWN -> try {
                login(username, password, localOwnerId)
            } catch (unauthorized: com.example.calendar.data.remote.auth.AuthApiException) {
                if (unauthorized.statusCode != 401) throw unauthorized
                try {
                    register(username, displayName, password, publicKey, localOwnerId)
                } catch (conflict: com.example.calendar.data.remote.auth.AuthApiException) {
                    if (conflict.statusCode != 409) throw conflict
                    login(username, password, localOwnerId)
                }
            }
        }
    }

    private suspend fun completeSession(ownerId: Long, token: String) {
        localUsers?.updateServerRegistrationStatus(ownerId, "REGISTERED")
        serverSessionManager.install(ownerId, token)
        val transport = webSocketTransport ?: return
        val url = webSocketUrl ?: return
        transport.connect(url, token)
    }
}
