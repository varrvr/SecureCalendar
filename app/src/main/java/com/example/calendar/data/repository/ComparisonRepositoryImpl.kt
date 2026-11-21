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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapNotNull
import org.json.JSONObject

class ComparisonRepositoryImpl(
    private val api: ComparisonApi,
    private val sessions: SessionRepository,
    private val serverSession: ServerSessionManager,
    private val webSocket: WebSocketTransport,
    private val events: com.example.calendar.domain.repository.EventRepository? = null
) : ComparisonRepository {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private val inboxes = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.channels.Channel<com.example.calendar.crypto.psi.PsiMessage>>()
    private val running = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private fun clearPsi() {
        inboxes.values.forEach { it.close(java.io.IOException("Comparison connection closed")) }
        inboxes.clear()
    }
    init {
        scope.launch {
            webSocket.incomingText.collect { raw ->
                runCatching {
                    val json = JSONObject(raw)
                    val id = json.optString("session_id")
                    val type = json.optString("type")
                    if (id.isBlank()) return@runCatching
                    if (type == "psi_oprf") {
                        val owner = sessions.activeUserId.value ?: return@runCatching
                        val localId = serverSession.serverUserIdFor(owner) ?: return@runCatching
                        val message = com.example.calendar.data.remote.websocket.PsiRelayCodec.decode(raw)
                        if (message.recipientId != localId) return@runCatching
                        // Bounded early-arrival buffers bridge REST reconciliation and peer readiness.
                        if (inboxes.size >= 16 && !inboxes.containsKey(id)) return@runCatching
                        val box = inboxes.computeIfAbsent(id) { kotlinx.coroutines.channels.Channel(64) }
                        if (!box.trySend(message).isSuccess) box.close(java.io.IOException("Comparison receive overflow"))
                    } else if (type in setOf("error", "peer_offline", "session_cancelled", "psi_abort")) {
                        inboxes[id]?.close(java.io.IOException("Comparison interrupted"))
                    }
                }
            }
        }
        scope.launch { webSocket.state.collect {
            if (it == WebSocketState.DISCONNECTED || it == WebSocketState.ERROR) clearPsi()
        } }
        scope.launch { combine(sessions.activeUserId, serverSession.owner) { local, owner -> local to owner }
            .collect { clearPsi() } }
    }
    override val connected: Flow<Boolean> = combine(webSocket.state, serverSession.owner, sessions.activeUserId) { state, owner, local ->
        state == WebSocketState.CONNECTED && owner != null && owner == local
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
        require(peer.id != serverSession.serverUserIdFor(owner)) { "Нельзя сравнить календарь с самим собой" }
        return api.createSession(peer.id, startEpochSeconds, endEpochSeconds, token).toDomain()
    }

    override suspend fun incomingRequests(): List<ComparisonInvite> {
        val owner = activeOwner()
        val token = requireToken(owner)
        val pending = api.listSessions(token)
            .filter { it.responderId == serverSession.serverUserIdFor(owner) && it.status == "pending" }
        if (pending.isEmpty()) return emptyList()

        val usernames = mutableMapOf<Long, String>()
        return pending.map { session ->
                val name = usernames.getOrPut(session.initiatorId) { api.getUser(session.initiatorId, token).username }
                session.toDomain().copy(initiatorUsername = name)
            }
    }

    override suspend fun accept(sessionId: String): ComparisonInvite =
        api.accept(sessionId, requireToken(activeOwner())).toDomain()

    override suspend fun reject(sessionId: String): ComparisonInvite =
        api.reject(sessionId, requireToken(activeOwner())).toDomain()

    override suspend fun cancel(sessionId: String): ComparisonInvite =
        api.cancel(sessionId, requireToken(activeOwner())).toDomain()

    override suspend fun preparePsi(sessionId: String): List<com.example.calendar.domain.model.TimeInterval> =
        withContext(kotlinx.coroutines.Dispatchers.Default) {
            val source = events ?: throw com.example.calendar.crypto.psi.PsiUnavailableException()
            val owner = activeOwner()
            val authGeneration = serverSession.generation()
            requireToken(owner)
            val invite = session(sessionId)
            check(invite.status == "accepted" || invite.status == "active")
            val localId = requireNotNull(serverSession.serverUserIdFor(owner))
            val initiator = invite.initiatorId == localId
            require(localId == invite.initiatorId || localId == invite.responderId)
            val start = java.time.Instant.ofEpochSecond(requireNotNull(invite.periodStartEpochSeconds))
            val end = java.time.Instant.ofEpochSecond(requireNotNull(invite.periodEndEpochSeconds))
            val period = com.example.calendar.domain.model.TimeInterval(start, end)
            val psiSession = com.example.calendar.crypto.psi.PsiSession(
                id = sessionId, localUserId = localId,
                peerUserId = if (initiator) invite.responderId else invite.initiatorId,
                role = if (initiator) com.example.calendar.crypto.psi.PsiRole.INITIATOR else com.example.calendar.crypto.psi.PsiRole.RESPONDER,
                periodStart = start, periodEnd = end)
            check(running.add(sessionId)) { "Comparison already running" }
            val box = inboxes.computeIfAbsent(sessionId) { kotlinx.coroutines.channels.Channel(64) }
            fun verifyOwner() {
                check(sessions.activeUserId.value == owner && serverSession.generation() == authGeneration)
                requireToken(owner)
            }
            try {
                val intervals = source.getIntervals(period, publicOnly = true)
                verifyOwner()
                val slots = com.example.calendar.crypto.SlotConverter.toSlots(intervals, period)
                val result = withTimeout(120000) {
                    com.example.calendar.crypto.psi.Bee2OprfComparison().run(psiSession, slots,
                        send = { packet ->
                            verifyOwner()
                            check(webSocket.sendText(com.example.calendar.data.remote.websocket.PsiRelayCodec.encode(packet))) { "Comparison connection lost" }
                        }, receive = { verifyOwner(); box.receive() })
                }
                verifyOwner()
                val merged = mutableListOf<com.example.calendar.domain.model.TimeInterval>()
                result.intersections.forEach { slot ->
                    val clippedStart = maxOf(start, slot)
                    val clippedEnd = minOf(end, slot.plusSeconds(com.example.calendar.crypto.SlotConverter.SLOT_SECONDS))
                    require(clippedStart < clippedEnd)
                    val previous = merged.lastOrNull()
                    if (previous != null && previous.end == clippedStart) {
                        merged[merged.lastIndex] = com.example.calendar.domain.model.TimeInterval(previous.start, clippedEnd)
                    } else merged += com.example.calendar.domain.model.TimeInterval(clippedStart, clippedEnd)
                }
                if (initiator) api.complete(sessionId, requireToken(owner))
                merged
            } catch (failure: Throwable) {
                // Notify the authenticated peer on failure/cancellation; never send slots or exception text.
                if (sessions.activeUserId.value == owner && serverSession.generation() == authGeneration) {
                    webSocket.sendText(com.example.calendar.data.remote.websocket.PsiRelayCodec.encode(
                        com.example.calendar.crypto.psi.PsiMessage(sessionId, "psi_abort", psiSession.peerUserId,
                            "comparison_aborted".encodeToByteArray())))
                }
                if (failure is LinkageError) throw com.example.calendar.crypto.psi.PsiUnavailableException(failure)
                throw failure
            } finally {
                inboxes.remove(sessionId, box); box.cancel(); running.remove(sessionId)
            }
        }

    override suspend fun session(sessionId: String): ComparisonInvite =
        api.getSession(sessionId, requireToken(activeOwner())).toDomain()

    private fun activeOwner(): Long = requireNotNull(sessions.activeUserId.value) { "Войдите в локальный аккаунт" }
    private fun requireToken(owner: Long): String =
        serverSession.tokenFor(owner) ?: error("Сначала подключитесь к серверу в профиле")

    private fun RemoteComparisonSession.toDomain() = ComparisonInvite(
        id, initiatorId, responderId, status, periodStart, periodEnd
    )
}
