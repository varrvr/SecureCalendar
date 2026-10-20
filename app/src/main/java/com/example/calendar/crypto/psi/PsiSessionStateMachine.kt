package com.example.calendar.crypto.psi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fail-closed lifecycle guard for a future PSI engine. It validates session routing and lifecycle
 * transitions only; it does not define cryptographic rounds or interpret payload bytes.
 */
class PsiSessionStateMachine(session: PsiSession) {
    val session: PsiSession = session.copy()

    private val _state = MutableStateFlow(session.state)
    val state: StateFlow<PsiSessionState> = _state.asStateFlow()

    @Synchronized
    fun outboundMessagePrepared() {
        transitionTo(PsiSessionState.WAITING_FOR_PEER)
    }

    @Synchronized
    fun acceptIncoming(message: PsiMessage) {
        check(_state.value == PsiSessionState.WAITING_FOR_PEER) {
            "Incoming PSI message is not expected in ${_state.value}"
        }
        require(message.sessionId == session.id) { "PSI message belongs to another session" }
        require(message.recipientId == session.localUserId) { "PSI message has a different recipient" }
        require(message.senderId == session.peerUserId) { "PSI message has an unauthenticated sender" }
        require(message.type.startsWith("psi_") && message.payload.isNotEmpty()) {
            "Malformed PSI relay message"
        }
        transitionTo(PsiSessionState.PROCESSING)
    }

    @Synchronized
    fun responsePrepared() {
        check(_state.value == PsiSessionState.PROCESSING) {
            "Cannot prepare a PSI response in ${_state.value}"
        }
        transitionTo(PsiSessionState.WAITING_FOR_PEER)
    }

    @Synchronized
    fun complete() = transitionTo(PsiSessionState.COMPLETE)

    @Synchronized
    fun cancel() = transitionTo(PsiSessionState.CANCELLED)

    @Synchronized
    fun fail() = transitionTo(PsiSessionState.FAILED)

    private fun transitionTo(next: PsiSessionState) {
        val current = _state.value
        val allowed = when (current) {
            PsiSessionState.CREATED -> setOf(
                PsiSessionState.WAITING_FOR_PEER,
                PsiSessionState.CANCELLED,
                PsiSessionState.FAILED
            )
            PsiSessionState.WAITING_FOR_PEER -> setOf(
                PsiSessionState.PROCESSING,
                PsiSessionState.CANCELLED,
                PsiSessionState.FAILED
            )
            PsiSessionState.PROCESSING -> setOf(
                PsiSessionState.WAITING_FOR_PEER,
                PsiSessionState.COMPLETE,
                PsiSessionState.CANCELLED,
                PsiSessionState.FAILED
            )
            PsiSessionState.COMPLETE,
            PsiSessionState.CANCELLED,
            PsiSessionState.FAILED -> emptySet()
        }
        check(next in allowed) { "Invalid PSI session transition: $current -> $next" }
        _state.value = next
    }
}
