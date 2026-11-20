package com.example.calendar

import android.util.Base64
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.calendar.crypto.Bee2Crypto
import com.example.calendar.data.crypto.*
import com.example.calendar.data.remote.auth.*
import com.example.calendar.data.remote.comparison.ComparisonApi
import com.example.calendar.data.remote.websocket.*
import com.example.calendar.data.repository.*
import com.example.calendar.presentation.comparison.*
import com.example.calendar.ui.theme.SecureCalendarTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class ComparisonFlowUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun bobSeesRealInviteAndAcceptsItWithoutFakePsiSuccess(): Unit = runBlocking {
        grantLocalNetworkForTests()
        val suffix = UUID.randomUUID().toString().replace("-", "").take(10)
        val aliceName = "ui_alice_$suffix"
        val bobName = "ui_bob_$suffix"
        val aliceOwner = SessionRepositoryImpl().apply { setActiveUser(800001) }
        val bobOwner = SessionRepositoryImpl().apply { setActiveUser(800002) }
        val aliceSession = ServerSessionManager()
        val bobSession = ServerSessionManager()
        val aliceWs = OkHttpWebSocketTransport(true)
        val bobWs = OkHttpWebSocketTransport(true)
        val endpoint = BuildConfig.SERVER_BASE_URL
        val wsUrl = endpoint.replaceFirst("http", "ws").trimEnd('/') + "/ws"
        val key = Bee2Crypto().generateKeyPair()
        val publicKey = Base64.encodeToString(key.publicKey, Base64.NO_WRAP)
        key.privateKey.fill(0)
        val api = ComparisonApi(endpoint)
        val aliceRepo = ComparisonRepositoryImpl(api, aliceOwner, aliceSession, aliceWs)
        val bobRepo = ComparisonRepositoryImpl(api, bobOwner, bobSession, bobWs)
        fun auth(session: ServerSessionManager, ws: WebSocketTransport) = ServerAuthRepositoryImpl(
            OpaqueClientImpl(OpaqueParameters.context, OpaqueParameters.serverIdentifier),
            AuthApi(endpoint), session, ws, { wsUrl })
        val store = androidx.lifecycle.ViewModelStore()
        lateinit var aliceVm: ComparisonViewModel
        lateinit var bobVm: ComparisonViewModel
        try {
            withTimeout(45000) {
                auth(aliceSession, aliceWs).register(aliceName, "Alice", "ui-test-password-$suffix", publicKey, 800001)
                auth(bobSession, bobWs).register(bobName, "Bob", "ui-test-password-$suffix", publicKey, 800002)
                withContext(Dispatchers.Main) {
                    aliceVm = ComparisonViewModel(aliceRepo)
                    bobVm = ComparisonViewModel(bobRepo)
                    store.put("alice", aliceVm); store.put("bob", bobVm)
                    aliceVm.selectAccount(800001); bobVm.selectAccount(800002)
                }
                aliceVm.state.first { it.isConnected }
                bobVm.state.first { it.isConnected }
                withContext(Dispatchers.Main) { aliceVm.selectUser(bobName); aliceVm.searchUsers() }
                aliceVm.state.first { !it.isSearching && it.selectedUserId != null }
                withContext(Dispatchers.Main) { aliceVm.requestComparison() }
                val invite = bobVm.state.first { it.incomingRequests.isNotEmpty() }.incomingRequests.single()
                assertEquals(aliceName, invite.username)
                compose.setContent {
                    val state by bobVm.state.collectAsState()
                    SecureCalendarTheme {
                        ComparisonScreen(state, bobVm::selectUser, bobVm::setPeriodStart, bobVm::setPeriodEnd,
                            bobVm::searchUsers, bobVm::requestComparison, bobVm::cancelRequest,
                            bobVm::acceptRequest, bobVm::rejectRequest, bobVm::retry)
                    }
                }
                val context = InstrumentationRegistry.getInstrumentation().targetContext
                compose.onNodeWithText(context.getString(R.string.comparison_invite_from, aliceName)).performScrollTo().assertIsDisplayed()
                compose.onNodeWithText(context.getString(R.string.comparison_accept)).performScrollTo().performClick()
                bobVm.state.first { it.errorRes == R.string.comparison_crypto_unavailable }
                aliceVm.state.first { it.errorRes == R.string.comparison_crypto_unavailable }
                assertTrue(aliceVm.state.value.intersections.isEmpty())
                assertEquals(ComparisonStatus.ERROR, aliceVm.state.value.status)
                compose.onNodeWithText(context.getString(R.string.comparison_crypto_unavailable)).performScrollTo().assertIsDisplayed()
                assertEquals("accepted", aliceRepo.session(invite.sessionId).status)
                bobRepo.cancel(invite.sessionId)
            }
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            aliceWs.disconnect(); bobWs.disconnect()
            aliceSession.clear(); bobSession.clear()
            key.publicKey.fill(0)
        }
    }
}
