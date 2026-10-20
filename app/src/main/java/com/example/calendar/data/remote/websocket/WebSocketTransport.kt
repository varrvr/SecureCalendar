package com.example.calendar.data.remote.websocket

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class WebSocketState { DISCONNECTED, CONNECTING, CONNECTED, CLOSING, ERROR }

interface WebSocketTransport {
    val state: StateFlow<WebSocketState>
    val incomingText: SharedFlow<String>
    val errors: SharedFlow<String>
    fun connect(webSocketBaseUrl: String, accessToken: String)
    fun sendText(message: String): Boolean
    fun disconnect()
}
