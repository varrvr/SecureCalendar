package com.example.calendar.data.remote.websocket

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Raw text transport. Message semantics remain the responsibility of the negotiated protocol. */
class OkHttpWebSocketTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
) : WebSocketTransport {
    private val _state = MutableStateFlow(WebSocketState.DISCONNECTED)
    override val state = _state.asStateFlow()
    private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val incomingText = _incoming.asSharedFlow()
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val errors = _errors.asSharedFlow()
    @Volatile private var socket: WebSocket? = null

    @Synchronized override fun connect(webSocketBaseUrl: String, accessToken: String) {
        disconnect()
        val input = runCatching { URI(webSocketBaseUrl) }.getOrNull()
            ?: throw IllegalArgumentException("Invalid WebSocket URL")
        require(input.scheme.equals("wss", ignoreCase = true)) { "WebSocket requires WSS" }
        require(accessToken.isNotBlank())
        require(!input.host.isNullOrBlank()) { "Invalid WebSocket URL" }
        val tokenParameter = "token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8.name())
        val query = listOfNotNull(input.rawQuery?.takeIf(String::isNotBlank), tokenParameter).joinToString("&")
        val url = URI("wss", input.userInfo, input.host, input.port, input.rawPath.ifBlank { "/" }, query, null).toASCIIString()
        _state.value = WebSocketState.CONNECTING
        socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) { _state.value = WebSocketState.CONNECTED }
            override fun onMessage(webSocket: WebSocket, text: String) { _incoming.tryEmit(text) }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) { _errors.tryEmit("Binary WebSocket messages are not supported by this transport") }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { _state.value = WebSocketState.CLOSING; webSocket.close(code, reason) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { if (socket === webSocket) socket = null; _state.value = WebSocketState.DISCONNECTED }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                if (socket === webSocket) socket = null
                _state.value = WebSocketState.ERROR
                _errors.tryEmit("WebSocket connection failed")
            }
        })
    }

    override fun sendText(message: String): Boolean = socket?.send(message) ?: false

    @Synchronized override fun disconnect() {
        val active = socket ?: return
        _state.value = WebSocketState.CLOSING
        socket = null
        active.close(1000, "client disconnect")
        _state.value = WebSocketState.DISCONNECTED
    }
}
