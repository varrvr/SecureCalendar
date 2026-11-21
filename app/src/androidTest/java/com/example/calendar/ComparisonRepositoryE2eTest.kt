package com.example.calendar

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.calendar.crypto.Bee2Crypto
import com.example.calendar.crypto.psi.PsiUnavailableException
import com.example.calendar.data.crypto.*
import com.example.calendar.data.remote.auth.*
import com.example.calendar.data.remote.comparison.ComparisonApi
import com.example.calendar.data.remote.websocket.*
import com.example.calendar.data.repository.*
import com.example.calendar.presentation.comparison.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Actual JNI public keys and Android OPAQUE against the Python server; no synthetic PSI. */
@RunWith(AndroidJUnit4::class)
class ComparisonRepositoryE2eTest {
    @org.junit.Before fun allowLocalNetwork() = grantLocalNetworkForTests()
    @Test fun realRoomCalendarsCompareThroughPythonAndBee2WithDifferentAccountIds() = runBlocking {
        withTimeout(45000) {
            val suffix = UUID.randomUUID().toString().replace("-", "").take(12)
            val aliceName = "repo_alice_$suffix"
            val bobName = "repo_bob_$suffix"
            val aliceSession = ServerSessionManager()
            val bobSession = ServerSessionManager()
            val aliceLocal = SessionRepositoryImpl()
            val bobLocal = SessionRepositoryImpl()
            aliceLocal.setActiveUser(700001)
            bobLocal.setActiveUser(900002)
            val aliceWs = OkHttpWebSocketTransport(true)
            val bobWs = OkHttpWebSocketTransport(true)
            val url = BuildConfig.SERVER_BASE_URL.replaceFirst("http", "ws").trimEnd('/') + "/ws"
            fun auth(manager: ServerSessionManager, ws: WebSocketTransport) = ServerAuthRepositoryImpl(
                OpaqueClientImpl(OpaqueParameters.context, OpaqueParameters.serverIdentifier),
                AuthApi(BuildConfig.SERVER_BASE_URL), manager, ws, { url })
            val key = Bee2Crypto().generateKeyPair()
            val publicKey = Base64.encodeToString(key.publicKey, Base64.NO_WRAP)
            key.privateKey.fill(0)
            val api = ComparisonApi(BuildConfig.SERVER_BASE_URL)
            val aliceCalendar = ComparisonTestCalendar(aliceLocal)
            val bobCalendar = ComparisonTestCalendar(bobLocal)
            val aliceRepo = ComparisonRepositoryImpl(api, aliceLocal, aliceSession, aliceWs, aliceCalendar.events)
            val bobRepo = ComparisonRepositoryImpl(api, bobLocal, bobSession, bobWs, bobCalendar.events)
            try {
                val alice = auth(aliceSession, aliceWs).register(aliceName, "Alice", "test-password-$suffix", publicKey, 700001)
                val bob = auth(bobSession, bobWs).register(bobName, "Bob", "test-password-$suffix", publicKey, 900002)
                assertNotEquals(700001L, alice.userId)
                assertNotEquals(900002L, bob.userId)
                aliceWs.state.first { it == WebSocketState.CONNECTED }
                bobWs.state.first { it == WebSocketState.CONNECTED }
                assertEquals(bob.userId, aliceRepo.searchUsers(bobName).single().id)
                val now = (System.currentTimeMillis() / 1000 / 900) * 900
                aliceCalendar.add(now, now + 1800)
                aliceCalendar.add(now + 1800, now + 2700, public = false)
                bobCalendar.add(now + 900, now + 2700)
                val invite = aliceRepo.createRequest(bobName, now, now + 3600)
                val inbox = bobRepo.incomingRequests().single { it.sessionId == invite.sessionId }
                assertEquals(aliceName, inbox.initiatorUsername)
                assertEquals(bob.userId, inbox.responderId)
                bobRepo.reject(invite.sessionId)
                assertEquals("rejected", aliceRepo.session(invite.sessionId).status)
                val accepted = aliceRepo.createRequest(bobName, now, now + 3600)
                bobRepo.accept(accepted.sessionId)
                assertEquals("accepted", aliceRepo.session(accepted.sessionId).status)
                val bobResult = async { bobRepo.preparePsi(accepted.sessionId) }
                val aliceResult = async { aliceRepo.preparePsi(accepted.sessionId) }
                val expected = listOf(com.example.calendar.domain.model.TimeInterval(
                    java.time.Instant.ofEpochSecond(now + 900), java.time.Instant.ofEpochSecond(now + 1800)))
                assertEquals(expected, aliceResult.await())
                assertEquals(expected, bobResult.await())
                assertEquals("completed", aliceRepo.session(accepted.sessionId).status)
                val cancelled = aliceRepo.createRequest(bobName, now, now + 3600)
                aliceRepo.cancel(cancelled.sessionId)
                assertEquals("cancelled", bobRepo.session(cancelled.sessionId).status)
                aliceLocal.clearSession()
                aliceSession.clear()
                assertNull(aliceSession.tokenFor(700001))
                assertNull(aliceSession.serverUserIdFor(700001))
            } finally {
                aliceWs.disconnect(); bobWs.disconnect()
                aliceSession.clear(); bobSession.clear()
                key.publicKey.fill(0)
                aliceCalendar.close(); bobCalendar.close()
            }
        }
    }
}
