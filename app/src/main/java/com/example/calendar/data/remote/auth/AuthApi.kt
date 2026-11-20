package com.example.calendar.data.remote.auth

import android.util.Base64
import android.util.Log
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class ServerAuthResult(
    val accessToken: String,
    val userId: Long,
    val username: String,
    val displayName: String,
    val expiresAtMillis: Long
)

data class ServerLoginChallenge(
    val loginStateId: String,
    val credentialResponse: ByteArray
)

class AuthApiException(val statusCode: Int, val detail: String) : IOException("HTTP $statusCode: $detail")

class AuthApi(private val baseUrlProvider: () -> String) {
    constructor(baseUrl: String) : this({ baseUrl })

    private companion object {
        const val TAG = "SecureCalendar.AuthHttp"
    }

    private val registrations = java.util.concurrent.ConcurrentHashMap<String, String>()

    suspend fun registrationStart(
        username: String,
        displayName: String,
        publicKey: String,
        message: ByteArray
    ): ByteArray {
        val request = JSONObject().apply {
            put("username", username)
            put("display_name", displayName)
            put("public_key", publicKey)
            put("registration_request", encode(message))
        }

        val response = post(
            path = "/auth/register/start",
            body = request
        )

        registrations[username] = response.getString("registration_state_id")
        return decode(response.getString("registration_response"))
    }

    suspend fun registrationFinish(
        username: String,
        message: ByteArray
    ): ServerAuthResult {
        val request = JSONObject().apply {
            put("username", username)
            put("registration_upload", encode(message))
            put("registration_state_id", registrations.remove(username) ?: error("Registration must be restarted"))
        }

        return parseAuthResult(
            post(
                path = "/auth/register/finish",
                body = request
            )
        )
    }

    suspend fun loginStart(
        username: String,
        message: ByteArray
    ): ServerLoginChallenge {
        val request = JSONObject().apply {
            put("username", username)
            put("credential_request", encode(message))
        }

        val response = post(
            path = "/auth/login/start",
            body = request
        )

        return ServerLoginChallenge(
            loginStateId = response.getString("login_state_id"),
            credentialResponse = decode(response.getString("credential_response"))
        )
    }

    suspend fun loginFinish(
        username: String,
        loginStateId: String,
        message: ByteArray
    ): ServerAuthResult {
        val request = JSONObject().apply {
            put("username", username)
            put("login_state_id", loginStateId)
            put("credential_finalization", encode(message))
        }

        return parseAuthResult(
            post(
                path = "/auth/login/finish",
                body = request
            )
        )
    }

    private fun parseAuthResult(
        response: JSONObject
    ): ServerAuthResult {
        val user = response.getJSONObject("user")

        return ServerAuthResult(
            accessToken = response.getString("access_token"),
            userId = user.getLong("id"),
            username = user.getString("username"),
            displayName = user.optString(
                "display_name",
                user.getString("username")
            ),
            expiresAtMillis = response.getLong("expires_at") * 1000L
        )
    }

    private suspend fun post(
        path: String,
        body: JSONObject
    ): JSONObject = withContext(Dispatchers.IO) {
        val endpoint = baseUrlProvider().trimEnd('/') + path
        val startedAt = SystemClock.elapsedRealtime()
        val requestBytes = body.toString().toByteArray(Charsets.UTF_8)
        Log.i(TAG, "POST started: $endpoint; requestBytes=${requestBytes.size}")
        val connection = try {
            URL(endpoint).openConnection() as HttpURLConnection
        } catch (failure: Exception) {
            Log.e(TAG, "Could not create HTTP connection for $path; ${failure.javaClass.simpleName}", failure)
            throw failure
        }

        try {
            connection.instanceFollowRedirects = false
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.doOutput = true
            connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=utf-8"
            )
            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            connection.outputStream.use { output ->
                output.write(requestBytes)
            }

            val code = connection.responseCode

            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseText = stream
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            Log.i(TAG, "POST response: $path; status=$code; responseBytes=${responseText.toByteArray(Charsets.UTF_8).size}; elapsedMs=${SystemClock.elapsedRealtime() - startedAt}")

            if (code !in 200..299) {
                val detail = runCatching { JSONObject(responseText).optString("detail", "Ошибка сервера") }
                    .getOrDefault("Ошибка сервера")
                throw AuthApiException(code, detail.take(240))
            }

            JSONObject(responseText)

        } catch (failure: Exception) {
            Log.e(TAG, "POST failed: $path; ${failure.javaClass.simpleName}; elapsedMs=${SystemClock.elapsedRealtime() - startedAt}", failure)
            throw failure

        } finally {
            requestBytes.fill(0)
            connection.disconnect()
        }
    }

    private fun encode(data: ByteArray): String {
        return Base64.encodeToString(
            data,
            Base64.NO_WRAP
        )
    }

    private fun decode(data: String): ByteArray {
        return Base64.decode(data, Base64.NO_WRAP)
    }
}
