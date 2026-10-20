package com.example.calendar.data.remote.comparison

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class RemoteUser(val id: Long, val username: String, val displayName: String, val publicKey: String)
data class RemoteComparisonSession(
    val id: String,
    val initiatorId: Long,
    val responderId: Long,
    val status: String,
    val periodStart: Long?,
    val periodEnd: Long?
)

class ComparisonApiException(val code: Int, val detail: String) : IOException("HTTP $code: $detail")

/** REST adapter for the existing Python /users and /sessions contract. */
class ComparisonApi(private val baseUrl: String) {
    suspend fun searchUsers(query: String, token: String): List<RemoteUser> {
        val encoded = URLEncoder.encode(query.trim(), Charsets.UTF_8.name())
        val result = request("GET", "/users?query=$encoded", token, null) as JSONArray
        return buildList {
            for (index in 0 until result.length()) add(result.getJSONObject(index).toUser())
        }
    }

    suspend fun listSessions(token: String): List<RemoteComparisonSession> {
        val result = request("GET", "/sessions", token, null) as JSONArray
        return buildList {
            for (index in 0 until result.length()) add(result.getJSONObject(index).toSession())
        }
    }

    suspend fun createSession(
        responderId: Long,
        periodStartSeconds: Long,
        periodEndSeconds: Long,
        token: String
    ): RemoteComparisonSession {
        val response = request(
            "POST", "/sessions", token,
            JSONObject().put("responder_id", responderId)
                .put("period_start", periodStartSeconds)
                .put("period_end", periodEndSeconds)
        ) as JSONObject
        return response.toSession()
    }

    suspend fun accept(sessionId: String, token: String) = sessionAction(sessionId, "accept", token)
    suspend fun reject(sessionId: String, token: String) = sessionAction(sessionId, "reject", token)
    suspend fun cancel(sessionId: String, token: String) = sessionAction(sessionId, "cancel", token)

    private suspend fun sessionAction(sessionId: String, action: String, token: String): RemoteComparisonSession =
        (request("POST", "/sessions/${encodePath(sessionId)}/$action", token, JSONObject()) as JSONObject).toSession()

    private suspend fun request(method: String, path: String, token: String, body: JSONObject?): Any =
        withContext(Dispatchers.IO) {
            val connection = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (status !in 200..299) {
                    val detail = runCatching { JSONObject(response).optString("detail", "Server request failed") }
                        .getOrDefault("Server request failed").take(200)
                    throw ComparisonApiException(status, detail)
                }
                JSONTokener(response).nextValue()
            } finally {
                connection.disconnect()
            }
        }

    private fun encodePath(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun JSONObject.toUser() = RemoteUser(
        id = getLong("id"), username = getString("username"),
        displayName = optString("display_name", getString("username")),
        publicKey = optString("public_key", "")
    )

    private fun JSONObject.toSession() = RemoteComparisonSession(
        id = getString("session_id"), initiatorId = getLong("initiator_id"),
        responderId = getLong("responder_id"), status = getString("status"),
        periodStart = if (isNull("period_start")) null else getLong("period_start"),
        periodEnd = if (isNull("period_end")) null else getLong("period_end")
    )
}

