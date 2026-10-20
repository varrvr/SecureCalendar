package com.example.calendar.crypto.psi

import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.Flow

enum class PsiRole { INITIATOR, RESPONDER }
enum class PsiSessionState { CREATED, WAITING_FOR_PEER, PROCESSING, COMPLETE, CANCELLED, FAILED }

data class PsiSession(
    val id: String = UUID.randomUUID().toString(),
    val localUserId: Long,
    val peerUserId: Long,
    val role: PsiRole,
    val periodStart: Instant,
    val periodEnd: Instant,
    val state: PsiSessionState = PsiSessionState.CREATED
) {
    init { require(localUserId > 0 && peerUserId > 0 && localUserId != peerUserId); require(periodEnd.isAfter(periodStart)) }
}

/** Payload is an opaque protocol message. It must never contain plaintext meeting data. */
data class PsiMessage(val sessionId: String, val type: String, val recipientId: Long, val payload: ByteArray) {
    init { require(sessionId.isNotBlank() && type.isNotBlank() && recipientId > 0) }
}

data class PsiResult(val intersections: List<Instant>, val completedAt: Instant)

interface PsiEngine {
    suspend fun begin(session: PsiSession, localSlots: Set<Long>): PsiMessage
    suspend fun receive(session: PsiSession, message: PsiMessage): PsiMessage?
    suspend fun result(session: PsiSession): PsiResult
    suspend fun cancel(sessionId: String)
    fun observeState(sessionId: String): Flow<PsiSessionState>
}

class PsiUnavailableException : UnsupportedOperationException("Защищённое сравнение пока недоступно")
