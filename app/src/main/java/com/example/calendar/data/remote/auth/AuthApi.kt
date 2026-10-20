package com.example.calendar.data.remote.auth

import android.util.Base64
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
    val displayName: String
)

data class ServerLoginChallenge(
    val loginStateId: String,
    val credentialResponse: ByteArray
)

class AuthApiException(val statusCode: Int, val detail: String) : IOException("HTTP $statusCode: $detail")

class AuthApi(
    private val baseUrl: String
) {

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

        return decode(response.getString("registration_response"))
    }

    suspend fun registrationFinish(
        username: String,
        message: ByteArray
    ): ServerAuthResult {
        val request = JSONObject().apply {
            put("username", username)
            put("registration_upload", encode(message))
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
            )
        )
    }

    private suspend fun post(
        path: String,
        body: JSONObject
    ): JSONObject = withContext(Dispatchers.IO) {

        val connection = URL(
            baseUrl.trimEnd('/') + path
        ).openConnection() as HttpURLConnection

        try {
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
                output.write(
                    body.toString().toByteArray(Charsets.UTF_8)
                )
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

            if (code !in 200..299) {
                val detail = runCatching { JSONObject(responseText).optString("detail", "Ошибка сервера") }
                    .getOrDefault("Ошибка сервера")
                throw AuthApiException(code, detail.take(240))
            }

            JSONObject(responseText)

        } finally {
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
