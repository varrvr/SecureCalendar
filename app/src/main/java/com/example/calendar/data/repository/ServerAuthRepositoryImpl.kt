package com.example.calendar.data.repository

import com.example.calendar.data.crypto.OpaqueClient
import com.example.calendar.data.remote.auth.AuthApi
import com.example.calendar.data.remote.auth.ServerAuthResult
import com.example.calendar.data.remote.auth.ServerSessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ServerAuthRepositoryImpl(
    private val opaqueClient: OpaqueClient,
    private val authApi: AuthApi,
    private val serverSessionManager: ServerSessionManager
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

        try {
            val start = opaqueClient.registrationStart(
                passwordBytes
            )
            state = start.state

            val serverResponse = authApi.registrationStart(
                username = username,
                displayName = displayName,
                publicKey = publicKey,
                message = start.message
            )

            val upload = opaqueClient.registrationFinish(
                password = passwordBytes,
                state = start.state,
                serverResponse = serverResponse
            )

            authApi.registrationFinish(
                username = username,
                message = upload
            ).also { result -> serverSessionManager.install(localOwnerId, result.accessToken) }

        } catch (e: CancellationException) {
            throw e
        } finally {
            passwordBytes.fill(0)
            state?.fill(0)
        }
    } }

    suspend fun login(
        username: String,
        password: String,
        localOwnerId: Long
    ): ServerAuthResult = opaqueMutex.withLock { withContext(Dispatchers.IO) {

        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        var state: ByteArray? = null

        try {
            val start = opaqueClient.loginStart(
                passwordBytes
            )
            state = start.state

            val serverResponse = authApi.loginStart(
                username = username,
                message = start.message
            )

            val finalMessage = opaqueClient.loginFinish(
                password = passwordBytes,
                state = start.state,
                serverResponse = serverResponse
            )

            authApi.loginFinish(
                username = username,
                message = finalMessage
            ).also { result -> serverSessionManager.install(localOwnerId, result.accessToken) }

        } catch (e: CancellationException) {
            throw e
        } finally {
            passwordBytes.fill(0)
            state?.fill(0)
        }
    } }
}
