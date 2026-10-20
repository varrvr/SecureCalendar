package com.example.calendar.data.repository

import com.example.calendar.data.remote.comparison.ComparisonApi
import com.example.calendar.data.remote.comparison.RemoteComparisonSession
import com.example.calendar.data.remote.websocket.WebSocketState
import com.example.calendar.data.remote.websocket.WebSocketTransport
import com.example.calendar.data.remote.auth.ServerSessionManager
import com.example.calendar.domain.model.ComparisonInvite
import com.example.calendar.domain.model.ComparisonPeer
import com.example.calendar.domain.model.ComparisonSignal
import com.example.calendar.domain.repository.ComparisonRepository
import com.example.calendar.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import org.json.JSONObject

class ComparisonRepositoryImpl(
    private val api: ComparisonApi,
    private val sessions: SessionRepository,
    private val serverSession: ServerSessionManager,
    private val webSocket: WebSocketTransport
) : ComparisonRepository {
    override val connected: Flow<Boolean> = webSocket.state.mapNotNull { state ->
        when (state) {
            WebSocketState.CONNECTED -> true
            WebSocketState.DISCONNECTED, WebSocketState.ERROR -> false
            else -> null
        }
    }

    override val signals: Flow<ComparisonSignal> = webSocket.incomingText.mapNotNull { raw ->
        runCatching {
            val message = JSONObject(raw)
            val type = message.optString("type")
            val sessionId = message.optString("session_id")
            if (type.isBlank() || sessionId.isBlank()) null
            else ComparisonSignal(type, sessionId, message.optLong("sender_id", 0L))
        }.getOrNull()
    }

    override suspend fun searchUsers(query: String): List<ComparisonPeer> {
        val owner = activeOwner()
        val token = requireToken(owner)
        return api.searchUsers(query, token).map { ComparisonPeer(it.id, it.username, it.displayName) }
    }

    override suspend fun createRequest(
        username: String,
        startEpochSeconds: Long,
        endEpochSeconds: Long
    ): ComparisonInvite {
        require(startEpochSeconds < endEpochSeconds) { "Invalid comparison period" }
        val owner = activeOwner()
        val token = requireToken(owner)
        val peer = api.searchUsers(username, token).firstOrNull { it.username.equals(username.trim(), true) }
            ?: error("Пользователь с таким логином не найден")
        require(peer.id != owner) { "Нельзя сравнить календарь с самим собой" }
        return api.createSession(peer.id, startEpochSeconds, endEpochSeconds, token).toDomain()
    }

    override suspend fun incomingRequests(): List<ComparisonInvite> {
        val owner = activeOwner()
        val token = requireToken(owner)
        val usernames = api.searchUsers("", token).associate { it.id to it.username }
        return api.listSessions(token)
            .filter { it.responderId == owner && it.status == "pending" }
            .map { it.toDomain().copy(initiatorUsername = usernames[it.initiatorId]) }
    }

    override suspend fun accept(sessionId: String): ComparisonInvite =
        api.accept(sessionId, requireToken(activeOwner())).toDomain()

    override suspend fun reject(sessionId: String): ComparisonInvite =
        api.reject(sessionId, requireToken(activeOwner())).toDomain()

    override suspend fun cancel(sessionId: String): ComparisonInvite =
        api.cancel(sessionId, requireToken(activeOwner())).toDomain()

    private fun activeOwner(): Long = requireNotNull(sessions.activeUserId.value) { "Войдите в локальный аккаунт" }
    private fun requireToken(owner: Long): String =
        serverSession.tokenFor(owner) ?: error("Сначала подключитесь к серверу в профиле")

    private fun RemoteComparisonSession.toDomain() = ComparisonInvite(
        id, initiatorId, responderId, status, periodStart, periodEnd
    )
}
