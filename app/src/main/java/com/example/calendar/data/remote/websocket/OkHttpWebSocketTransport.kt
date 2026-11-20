package com.example.calendar.data.remote.websocket

import android.util.Log
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
    private val allowInsecureForDebug: Boolean = false,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
) : WebSocketTransport {
    private companion object {
        const val TAG = "SecureCalendar.WebSocket"
    }
    private val _state = MutableStateFlow(WebSocketState.DISCONNECTED)
    override val state = _state.asStateFlow()
    private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val incomingText = _incoming.asSharedFlow()
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val errors = _errors.asSharedFlow()
    @Volatile private var socket: WebSocket? = null

    @Synchronized override fun connect(webSocketBaseUrl: String, accessToken: String) {
        Log.i(TAG, "Connect requested; endpoint=$webSocketBaseUrl; tokenPresent=${accessToken.isNotBlank()}; token omitted")
        disconnect()
        val input = runCatching { URI(webSocketBaseUrl) }.getOrNull()
            ?: throw IllegalArgumentException("Invalid WebSocket URL")
        val secure = input.scheme.equals("wss", ignoreCase = true)
        val insecureDebug = allowInsecureForDebug && input.scheme.equals("ws", ignoreCase = true)
        require(secure || insecureDebug) { "WebSocket requires WSS" }
        require(accessToken.isNotBlank())
        require(!input.host.isNullOrBlank()) { "Invalid WebSocket URL" }
        val tokenParameter = "token=" + URLEncoder.encode(accessToken, StandardCharsets.UTF_8.name())
        val query = listOfNotNull(input.rawQuery?.takeIf(String::isNotBlank), tokenParameter).joinToString("&")
        val scheme = if (secure) "wss" else "ws"
        val url = URI(scheme, input.userInfo, input.host, input.port, input.rawPath.ifBlank { "/" }, query, null).toASCIIString()
        _state.value = WebSocketState.CONNECTING
        Log.d(TAG, "Opening WebSocket; scheme=$scheme; host=${input.host}; path=${input.rawPath}; auth token redacted")
        socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Log.i(TAG, "WebSocket connected; status=${response.code}")
                _state.value = WebSocketState.CONNECTED
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Text frame received; chars=${text.length}; payload omitted")
                _incoming.tryEmit(text)
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.w(TAG, "Binary frame received; bytes=${bytes.size}; binary transport is not implemented")
                _errors.tryEmit("Binary WebSocket messages are not supported by this transport")
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing; code=$code; reason omitted")
                _state.value = WebSocketState.CLOSING
                webSocket.close(code, reason)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed; code=$code; reason omitted")
                if (socket === webSocket) socket = null
                _state.value = WebSocketState.DISCONNECTED
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                if (socket === webSocket) socket = null
                _state.value = WebSocketState.ERROR
                Log.e(TAG, "WebSocket failure; error=${t.javaClass.simpleName}; httpStatus=${response?.code}; message omitted to avoid token leakage")
                _errors.tryEmit("WebSocket connection failed")
            }
        })
    }

    override fun sendText(message: String): Boolean {
        val active = socket
        if (active == null) {
            Log.w(TAG, "Send rejected: no active WebSocket; chars=${message.length}; payload omitted")
            return false
        }
        val accepted = active.send(message)
        Log.d(TAG, "Text frame queued=$accepted; chars=${message.length}; payload omitted")
        return accepted
    }

    @Synchronized override fun disconnect() {
        val active = socket ?: return
        Log.i(TAG, "WebSocket disconnect requested")
        _state.value = WebSocketState.CLOSING
        socket = null
        active.close(1000, "client disconnect")
        _state.value = WebSocketState.DISCONNECTED
    }
}
