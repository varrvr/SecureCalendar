package com.example.calendar

import com.example.calendar.data.remote.websocket.OkHttpWebSocketTransport
import com.example.calendar.data.remote.websocket.WebSocketState
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.security.KeyStore
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class WebSocketMitmTest {
    @Test fun redirectCannotForwardBearerTokenToAnotherEndpoint() = runBlocking<Unit> {
        val loopback = InetAddress.getByName("127.0.0.1")
        val executor = Executors.newSingleThreadExecutor()
        val transport = OkHttpWebSocketTransport(allowInsecureForDebug = true)
        try {
            ServerSocket(0, 1, loopback).use { source ->
                ServerSocket(0, 1, loopback).use { target ->
                    source.soTimeout = 5000
                    target.soTimeout = 500
                    val response = executor.submit<Boolean> {
                        source.accept().use { socket ->
                            socket.soTimeout = 5000
                            val reader = socket.getInputStream().bufferedReader()
                            val request = reader.readLine()
                            while (!reader.readLine().isNullOrEmpty()) { }
                            socket.getOutputStream().write(("HTTP/1.1 302 Found\r\n" +
                                "Location: http://127.0.0.1:${target.localPort}/ws?token=test-only\r\n" +
                                "Content-Length: 0\r\nConnection: close\r\n\r\n").toByteArray())
                            request.contains("token=test-only")
                        }
                    }
                    transport.connect("ws://127.0.0.1:${source.localPort}/ws", "test-only")
                    withTimeout(5000) { transport.state.first { it == WebSocketState.ERROR } }
                    assertTrue(response.get(5, TimeUnit.SECONDS))
                    transport.disconnect() // Cancel reconnect before observing the redirect target.
                    assertThrows(SocketTimeoutException::class.java) { target.accept().close() }
                }
            }
        } finally { transport.disconnect(); executor.shutdownNow() }
    }

    @Test fun untrustedTlsServerIsRejectedBeforeAnyHttpOrTokenIsSent() = runBlocking<Unit> {
        val keyStore = KeyStore.getInstance("PKCS12")
        javaClass.getResourceAsStream("/tls/untrusted-server.p12")!!.use {
            keyStore.load(it, "test-only".toCharArray())
        }
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keys.init(keyStore, "test-only".toCharArray())
        val tls = SSLContext.getInstance("TLS")
        tls.init(keys.keyManagers, null, null)
        val executor = Executors.newSingleThreadExecutor()
        val transport = OkHttpWebSocketTransport()
        try {
            tls.serverSocketFactory.createServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 5000
                val noHttp = executor.submit<Boolean> {
                    (server.accept() as SSLSocket).use { socket ->
                        socket.soTimeout = 5000
                        try {
                            socket.startHandshake()
                            socket.getInputStream().read() == -1
                        } catch (_: SSLException) { true }
                        catch (_: java.net.SocketException) { true }
                    }
                }
                transport.connect("wss://127.0.0.1:${server.localPort}/ws", "test-only")
                withTimeout(5000) { transport.state.first { it == WebSocketState.ERROR } }
                assertTrue(noHttp.get(5, TimeUnit.SECONDS))
            }
        } finally { transport.disconnect(); executor.shutdownNow() }
    }
}
