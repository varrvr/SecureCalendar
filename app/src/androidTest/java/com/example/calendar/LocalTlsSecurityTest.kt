package com.example.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.calendar.data.remote.comparison.ComparisonApi
import com.example.calendar.data.remote.websocket.OkHttpWebSocketTransport
import com.example.calendar.data.remote.websocket.WebSocketState
import java.net.InetAddress
import java.security.KeyStore
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** The debug CA must not turn into trust-all, tested with a different self-signed certificate. */
@RunWith(AndroidJUnit4::class)
class LocalTlsSecurityTest {
    private suspend fun rejectedServer(action: suspend (String) -> Unit) {
        grantLocalNetworkForTests()
        val store = KeyStore.getInstance("PKCS12")
        InstrumentationRegistry.getInstrumentation().context.assets.open("untrusted-server.p12").use {
            store.load(it, "test-only".toCharArray())
        }
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keys.init(store, "test-only".toCharArray())
        val tls = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) }
        val executor = Executors.newSingleThreadExecutor()
        try {
            tls.serverSocketFactory.createServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 5000
                val noHttp = executor.submit<Boolean> {
                    (server.accept() as SSLSocket).use { socket ->
                        socket.soTimeout = 5000
                        try { socket.startHandshake(); socket.inputStream.read() == -1 }
                        catch (_: SSLException) { true }
                        catch (_: java.net.SocketException) { true }
                    }
                }
                action("127.0.0.1:${server.localPort}")
                assertTrue("No bearer token or HTTP may reach an untrusted TLS server", noHttp.get(5, TimeUnit.SECONDS))
            }
        } finally { executor.shutdownNow() }
    }
    @Test fun restRejectsUntrustedCertificateDespiteLocalDebugCa() = runBlocking<Unit> {
        rejectedServer { address ->
            try {
                ComparisonApi("https://$address").searchUsers("test", "test-only")
                fail("Untrusted TLS certificate accepted")
            } catch (expected: SSLException) { /* Platform rejected the chain. */ }
        }
    }
    @Test fun webSocketRejectsUntrustedCertificateDespiteLocalDebugCa() = runBlocking<Unit> {
        rejectedServer { address ->
            val socket = OkHttpWebSocketTransport()
            try {
                socket.connect("wss://$address/ws", "test-only")
                withTimeout(5000) { socket.state.first { it == WebSocketState.ERROR } }
            } finally { socket.disconnect() }
        }
    }
}
