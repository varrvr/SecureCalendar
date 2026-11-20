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
    @Test fun differentLocalAndRemoteIdsStillDeliverRequestsAndFailClosedAtPsi() = runBlocking {
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
            val aliceRepo = ComparisonRepositoryImpl(api, aliceLocal, aliceSession, aliceWs)
            val bobRepo = ComparisonRepositoryImpl(api, bobLocal, bobSession, bobWs)
            try {
                val alice = auth(aliceSession, aliceWs).register(aliceName, "Alice", "test-password-$suffix", publicKey, 700001)
                val bob = auth(bobSession, bobWs).register(bobName, "Bob", "test-password-$suffix", publicKey, 900002)
                assertNotEquals(700001L, alice.userId)
                assertNotEquals(900002L, bob.userId)
                aliceWs.state.first { it == WebSocketState.CONNECTED }
                bobWs.state.first { it == WebSocketState.CONNECTED }
                assertEquals(bob.userId, aliceRepo.searchUsers(bobName).single().id)
                val now = System.currentTimeMillis() / 1000
                val invite = aliceRepo.createRequest(bobName, now, now + 3600)
                val inbox = bobRepo.incomingRequests().single { it.sessionId == invite.sessionId }
                assertEquals(aliceName, inbox.initiatorUsername)
                assertEquals(bob.userId, inbox.responderId)
                bobRepo.reject(invite.sessionId)
                assertEquals("rejected", aliceRepo.session(invite.sessionId).status)
                val accepted = aliceRepo.createRequest(bobName, now, now + 3600)
                bobRepo.accept(accepted.sessionId)
                assertEquals("accepted", aliceRepo.session(accepted.sessionId).status)
                try {
                    aliceRepo.preparePsi(accepted.sessionId)
                    fail("Missing PSI engine must never report an intersection")
                } catch (_: PsiUnavailableException) { }
                bobRepo.cancel(accepted.sessionId)
                assertEquals("cancelled", aliceRepo.session(accepted.sessionId).status)
                aliceLocal.clearSession()
                aliceSession.clear()
                assertNull(aliceSession.tokenFor(700001))
                assertNull(aliceSession.serverUserIdFor(700001))
            } finally {
                aliceWs.disconnect(); bobWs.disconnect()
                aliceSession.clear(); bobSession.clear()
                key.publicKey.fill(0)
            }
        }
    }
}
