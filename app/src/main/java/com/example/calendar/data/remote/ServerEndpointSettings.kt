package com.example.calendar.data.remote

import android.content.Context
import com.example.calendar.BuildConfig
import java.net.URI

/** One endpoint for REST and WebSocket; release never accepts cleartext. */
class ServerEndpointSettings(context: Context, private val defaultUrl: String) {
    private val prefs = context.applicationContext.getSharedPreferences("server_endpoint", Context.MODE_PRIVATE)
    fun current(): String = normalize(prefs.getString("base_url", null) ?: defaultUrl)
    fun update(value: String): String = normalize(value).also { prefs.edit().putString("base_url", it).apply() }
    fun requiresLocalNetworkPermission(): Boolean {
        val host = URI(current()).host.lowercase().trim('[', ']')
        val parts = host.split('.')
        return host == "localhost" || host == "::1" || host.startsWith("10.") ||
            host.startsWith("127.") || host.startsWith("192.168.") || host.startsWith("169.254.") ||
            (parts.size == 4 && parts[0] == "172" && (parts[1].toIntOrNull() ?: 0) in 16..31) ||
            host.startsWith("fc") && host.contains(':') || host.startsWith("fd") && host.contains(':') ||
            host.startsWith("fe80:") || host.endsWith(".local") || !host.contains('.')
    }
    fun webSocketUrl(): String {
        val uri = URI(current())
        return URI(if (uri.scheme == "https") "wss" else "ws", null, uri.host, uri.port,
            uri.path.trimEnd('/') + "/ws", null, null).toASCIIString()
    }
    private fun normalize(value: String): String {
        val uri = try { URI(value.trim().trimEnd('/')) } catch (_: Exception) {
            throw IllegalArgumentException("Некорректный адрес сервера")
        }
        require(uri.scheme == "https" || (BuildConfig.DEBUG && uri.scheme == "http")) {
            "Требуется HTTPS; HTTP разрешён только в debug"
        }
        require(!uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) {
            "Укажите адрес сервера без пароля, параметров и фрагмента"
        }
        require(uri.port == -1 || uri.port in 1..65535) { "Некорректный порт сервера" }
        return uri.toASCIIString()
    }
}
