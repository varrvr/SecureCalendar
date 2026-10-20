package com.example.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.calendar.data.crypto.OpaqueClientImpl
import com.example.calendar.data.crypto.OpaqueParameters
import com.example.calendar.data.remote.auth.AuthApi
import com.example.calendar.data.remote.comparison.ComparisonApi
import com.example.calendar.data.remote.websocket.OkHttpWebSocketTransport
import com.example.calendar.data.remote.websocket.WebSocketState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Transport/auth integration only. The test payload is synthetic and contains no calendar data. */
@RunWith(AndroidJUnit4::class)
class TwoUserServerE2eTest {
    private val auth = AuthApi("http://10.0.2.2:8000")
    private val comparison = ComparisonApi("http://10.0.2.2:8000")

    @Test
    fun twoOpaqueUsersSearchRequestAcceptAndRelayOpaqueTestMessage() = runBlocking {
        val suffix = UUID.randomUUID().toString().replace("-", "").take(12)
        val alice = registerAndLogin("alice_$suffix")
        val bob = registerAndLogin("bob_$suffix")

        val found = comparison.searchUsers("bob_$suffix", alice.accessToken)
            .single { it.username == "bob_$suffix" }
        val aliceTransport = OkHttpWebSocketTransport(allowInsecureForDebug = true)
        val bobTransport = OkHttpWebSocketTransport(allowInsecureForDebug = true)
        try {
            val aliceHello = async { withTimeout(10_000) { aliceTransport.incomingText.first { isType(it, "connected") } } }
            val bobHello = async { withTimeout(10_000) { bobTransport.incomingText.first { isType(it, "connected") } } }
            aliceTransport.connect("ws://10.0.2.2:8000/ws", alice.accessToken)
            bobTransport.connect("ws://10.0.2.2:8000/ws", bob.accessToken)
            aliceTransport.state.first { it == WebSocketState.CONNECTED }
            bobTransport.state.first { it == WebSocketState.CONNECTED }
            assertTrue(isType(aliceHello.await(), "connected"))
            assertTrue(isType(bobHello.await(), "connected"))

            val requestEvent = async {
                withTimeout(10_000) { bobTransport.incomingText.first { isType(it, "session_request") } }
            }
            val now = System.currentTimeMillis() / 1000L
            val session = comparison.createSession(found.id, now, now + 86_400, alice.accessToken)
            assertEquals("pending", session.status)
            assertEquals(session.id, JSONObject(requestEvent.await()).getString("session_id"))
            assertTrue(comparison.listSessions(bob.accessToken).any { it.id == session.id && it.status == "pending" })

            val acceptedEvent = async {
                withTimeout(10_000) { aliceTransport.incomingText.first { isType(it, "session_accepted") } }
            }
            comparison.accept(session.id, bob.accessToken)
            assertEquals(session.id, JSONObject(acceptedEvent.await()).getString("session_id"))

            val payloadEvent = async {
                withTimeout(10_000) { bobTransport.incomingText.first { isType(it, "transport_probe") } }
            }
            val syntheticPayload = android.util.Base64.encodeToString(
                "non-secret transport test".toByteArray(), android.util.Base64.NO_WRAP
            )
            val sent = aliceTransport.sendText(
                JSONObject().put("type", "transport_probe")
                    .put("session_id", session.id)
                    .put("recipient_id", found.id)
                    .put("payload", syntheticPayload).toString()
            )
            assertTrue(sent)
            val relayed = JSONObject(payloadEvent.await())
            assertEquals(session.id, relayed.getString("session_id"))
            assertEquals(alice.userId, relayed.getLong("sender_id"))
            assertEquals(syntheticPayload, relayed.getString("payload"))
        } finally {
            aliceTransport.disconnect()
            bobTransport.disconnect()
        }
    }

    private suspend fun registerAndLogin(username: String): com.example.calendar.data.remote.auth.ServerAuthResult {
        val password = "opaque-e2e-${UUID.randomUUID()}"
        val opaque = OpaqueClientImpl(OpaqueParameters.context, OpaqueParameters.serverIdentifier)
        val regStart = opaque.registrationStart(password.toByteArray())
        val regResponse = auth.registrationStart(username, username, "synthetic-test-public-key", regStart.message)
        val upload = opaque.registrationFinish(username, password.toByteArray(), regStart.state, regResponse)
        auth.registrationFinish(username, upload)

        val loginStart = opaque.loginStart(password.toByteArray())
        val challenge = auth.loginStart(username, loginStart.message)
        val finish = opaque.loginFinish(username, password.toByteArray(), loginStart.state, challenge.credentialResponse)
        return auth.loginFinish(username, challenge.loginStateId, finish)
    }

    private fun isType(raw: String, type: String) = runCatching { JSONObject(raw).optString("type") == type }.getOrDefault(false)
}
