package com.example.calendar

import com.example.calendar.crypto.psi.PsiMessage
import com.example.calendar.crypto.psi.PsiRole
import com.example.calendar.crypto.psi.PsiSession
import com.example.calendar.crypto.psi.PsiSessionState
import com.example.calendar.crypto.psi.PsiSessionStateMachine
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PsiSessionStateMachineTest {
    private fun machine() = PsiSessionStateMachine(
        PsiSession(
            id = "session-1",
            localUserId = 10,
            peerUserId = 20,
            role = PsiRole.INITIATOR,
            periodStart = Instant.parse("2026-01-01T00:00:00Z"),
            periodEnd = Instant.parse("2026-01-02T00:00:00Z")
        )
    )

    @Test
    fun validatesMessageRoutingAndLifecycle() {
        val machine = machine()
        machine.outboundMessagePrepared()
        assertEquals(PsiSessionState.WAITING_FOR_PEER, machine.state.value)
        machine.acceptIncoming(PsiMessage("session-1", "psi_round1", 10, byteArrayOf(1), 20))
        assertEquals(PsiSessionState.PROCESSING, machine.state.value)
        machine.responsePrepared()
        machine.acceptIncoming(PsiMessage("session-1", "psi_round2", 10, byteArrayOf(2), 20))
        machine.complete()
        assertEquals(PsiSessionState.COMPLETE, machine.state.value)
    }

    @Test
    fun rejectsWrongSessionPeerAndOutOfOrderMessages() {
        val machine = machine()
        assertThrows(IllegalStateException::class.java) {
            machine.acceptIncoming(PsiMessage("session-1", "psi_round1", 10, byteArrayOf(1), 20))
        }
        machine.outboundMessagePrepared()
        assertThrows(IllegalArgumentException::class.java) {
            machine.acceptIncoming(PsiMessage("other-session", "psi_round1", 10, byteArrayOf(1), 20))
        }
        assertThrows(IllegalArgumentException::class.java) {
            machine.acceptIncoming(PsiMessage("session-1", "psi_round1", 10, byteArrayOf(1), 99))
        }
        machine.acceptIncoming(PsiMessage("session-1", "psi_round1", 10, byteArrayOf(1), 20))
        machine.cancel()
        assertThrows(IllegalStateException::class.java) { machine.complete() }
    }
}
