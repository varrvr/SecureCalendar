package com.example.calendar.data.remote.websocket

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okio.ByteString
import java.net.URI
import java.util.concurrent.TimeUnit

/** One authenticated socket, bounded reconnect, heartbeat and stale-listener isolation. */
class OkHttpWebSocketTransport(
    private val allowInsecureForDebug: Boolean = false,
    client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS).build()
) : WebSocketTransport {
    // The upgrade URL carries a bearer token. Never follow redirects, including TLS downgrade.
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(WebSocketState.DISCONNECTED)
    override val state = _state.asStateFlow()
    private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val incomingText = _incoming.asSharedFlow()
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val errors = _errors.asSharedFlow()
    private var socket: WebSocket? = null
    private var reconnect: Job? = null
    private var generation = 0L
    private var request: Request? = null
    private var attempts = 0

    @Synchronized override fun connect(webSocketBaseUrl: String, accessToken: String) {
        disconnect()
        val input = URI(webSocketBaseUrl)
        require(input.scheme == "wss" || (allowInsecureForDebug && input.scheme == "ws")) { "WebSocket requires WSS" }
        require(!input.host.isNullOrBlank() && input.userInfo == null && input.query == null && accessToken.isNotBlank())
        // The existing server contract authenticates the upgrade using the token query.
        val url = okhttp3.HttpUrl.Builder().scheme(if (input.scheme == "wss") "https" else "http")
            .host(input.host).apply { if (input.port != -1) port(input.port) }
            .encodedPath(input.rawPath.ifBlank { "/" }).addQueryParameter("token", accessToken).build()
        request = Request.Builder().url(url).build()
        attempts = 0
        open(generation)
    }

    @Synchronized private fun open(id: Long) {
        val pending = request ?: return
        if (id != generation) return
        _state.value = WebSocketState.CONNECTING
        socket = client.newWebSocket(pending, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = synchronized(this@OkHttpWebSocketTransport) {
                if (id != generation || socket !== webSocket) { webSocket.cancel(); return@synchronized }
                attempts = 0
                _state.value = WebSocketState.CONNECTED
            }
            override fun onMessage(webSocket: WebSocket, text: String) = synchronized(this@OkHttpWebSocketTransport) {
                if (id != generation || socket !== webSocket) return@synchronized
                if (!_incoming.tryEmit(text)) {
                    _errors.tryEmit("receive_buffer_overflow")
                    webSocket.close(1013, "receiver overloaded")
                }
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (id == generation) webSocket.close(1003, "text messages required")
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, "")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                lost(id, webSocket, code == 1008)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                // Never log Throwable or Request: the upgrade URL contains the bearer token.
                lost(id, webSocket, response?.code == 401 || response?.code == 403)
            }
        })
    }

    @Synchronized private fun lost(id: Long, failed: WebSocket, unauthorized: Boolean) {
        if (id != generation || socket !== failed) return
        socket = null
        _state.value = WebSocketState.ERROR
        _errors.tryEmit(if (unauthorized) "authentication_expired" else "connection_lost")
        if (unauthorized || attempts >= 5 || request == null) { request = null; return }
        val delayMillis = (1000L shl attempts++).coerceAtMost(16000L)
        reconnect?.cancel()
        reconnect = scope.launch { delay(delayMillis); open(id) }
    }

    @Synchronized override fun sendText(message: String): Boolean =
        _state.value == WebSocketState.CONNECTED && socket?.send(message) == true

    @Synchronized override fun disconnect() {
        generation++
        reconnect?.cancel(); reconnect = null
        request = null
        val previous = socket; socket = null
        previous?.cancel()
        _state.value = WebSocketState.DISCONNECTED
    }
}
