package com.example.calendar.data.repository

import android.util.Log
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
    private val webSocketUrl: (() -> String)? = null,
    private val localUsers: LocalUserDao? = null,
    private val activeLocalOwner: (() -> Long?)? = null
) {
    private val opaqueMutex = Mutex()
    private val tag = "SecureCalendar.OPAQUE"

    suspend fun register(
        username: String,
        displayName: String,
        password: String,
        publicKey: String,
        localOwnerId: Long
    ): ServerAuthResult = opaqueMutex.withLock { withContext(Dispatchers.IO) {

        Log.i(tag, "Registration flow started; localOwnerId=$localOwnerId")
        val generation = serverSessionManager.generation()
        check(activeLocalOwner == null || activeLocalOwner.invoke() == localOwnerId)
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        var state: ByteArray? = null
        var requestMessage: ByteArray? = null
        var serverResponse: ByteArray? = null
        var upload: ByteArray? = null

        try {
            Log.d(tag, "Calling OPAQUE clientRegistrationStart")
            val start = opaqueClient.registrationStart(
                passwordBytes
            )
            state = start.state
            requestMessage = start.message
            Log.d(tag, "OPAQUE registration request created; stateBytes=${state.size}; messageBytes=${requestMessage.size}")

            Log.d(tag, "Sending registration start to server")
            val responseBytes = authApi.registrationStart(
                username = username,
                displayName = displayName,
                publicKey = publicKey,
                message = start.message
            )
            serverResponse = responseBytes
            Log.d(tag, "Registration challenge received; bytes=${serverResponse.size}")

            Log.d(tag, "Calling OPAQUE clientRegistrationFinish")
            val uploadBytes = opaqueClient.registrationFinish(
                username = username,
                password = passwordBytes,
                state = start.state,
                serverResponse = responseBytes
            )
            upload = uploadBytes
            Log.d(tag, "OPAQUE registration upload created; bytes=${upload.size}")

            Log.d(tag, "Sending registration finish to server")
            authApi.registrationFinish(
                username = username,
                message = uploadBytes
            ).also { result -> completeSession(localOwnerId, result, generation) }

        } catch (e: CancellationException) {
            Log.i(tag, "Registration flow cancelled; localOwnerId=$localOwnerId")
            throw e
        } catch (failure: Exception) {
            Log.e(tag, "Registration flow failed; localOwnerId=$localOwnerId; ${failure.javaClass.simpleName}", failure)
            throw failure
        } finally {
            passwordBytes.fill(0)
            state?.fill(0)
            requestMessage?.fill(0)
            serverResponse?.fill(0)
            upload?.fill(0)
            Log.d(tag, "Registration temporary buffers cleared")
        }
    } }

    suspend fun login(
        username: String,
        password: String,
        localOwnerId: Long
    ): ServerAuthResult = opaqueMutex.withLock { withContext(Dispatchers.IO) {

        Log.i(tag, "Login flow started; localOwnerId=$localOwnerId")
        val generation = serverSessionManager.generation()
        check(activeLocalOwner == null || activeLocalOwner.invoke() == localOwnerId)
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        var state: ByteArray? = null
        var requestMessage: ByteArray? = null
        var serverResponse: ByteArray? = null
        var finalMessage: ByteArray? = null

        try {
            Log.d(tag, "Calling OPAQUE clientLoginStart")
            val start = opaqueClient.loginStart(
                passwordBytes
            )
            state = start.state
            requestMessage = start.message
            Log.d(tag, "OPAQUE credential request created; stateBytes=${state.size}; messageBytes=${requestMessage.size}")

            Log.d(tag, "Sending login start to server")
            val challenge = authApi.loginStart(
                username = username,
                message = requestMessage
            )
            serverResponse = challenge.credentialResponse
            Log.d(tag, "Login challenge received; stateIdPresent=${challenge.loginStateId.isNotBlank()}; responseBytes=${serverResponse.size}")

            Log.d(tag, "Calling OPAQUE clientLoginFinish")
            val finalization = opaqueClient.loginFinish(
                username = username,
                password = passwordBytes,
                state = start.state,
                serverResponse = challenge.credentialResponse
            )
            finalMessage = finalization
            Log.d(tag, "OPAQUE login finalization created; bytes=${finalMessage.size}")

            Log.d(tag, "Sending login finish to server")
            authApi.loginFinish(
                username = username,
                loginStateId = challenge.loginStateId,
                message = finalization
            ).also { result -> completeSession(localOwnerId, result, generation) }

        } catch (e: CancellationException) {
            Log.i(tag, "Login flow cancelled; localOwnerId=$localOwnerId")
            throw e
        } catch (failure: Exception) {
            Log.e(tag, "Login flow failed; localOwnerId=$localOwnerId; ${failure.javaClass.simpleName}", failure)
            throw failure
        } finally {
            passwordBytes.fill(0)
            state?.fill(0)
            requestMessage?.fill(0)
            serverResponse?.fill(0)
            finalMessage?.fill(0)
            Log.d(tag, "Login temporary buffers cleared")
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
        Log.i(tag, "Selecting server auth flow; status=$registrationStatus; localOwnerId=$localOwnerId")
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

    private suspend fun completeSession(ownerId: Long, result: ServerAuthResult, generation: Long) {
        check(activeLocalOwner == null || activeLocalOwner.invoke() == ownerId) { "Local account changed during authentication" }
        Log.i(tag, "Installing authenticated server session; localOwnerId=$ownerId; token omitted")
        localUsers?.updateServerRegistrationStatus(ownerId, "REGISTERED")
        synchronized(serverSessionManager) {
            check(activeLocalOwner == null || activeLocalOwner.invoke() == ownerId)
            serverSessionManager.install(ownerId, result.accessToken, result.userId, result.expiresAtMillis, generation)
            val transport = webSocketTransport
            val url = webSocketUrl?.invoke()
            if (transport != null && url != null) transport.connect(url, result.accessToken)
        }
    }
}
