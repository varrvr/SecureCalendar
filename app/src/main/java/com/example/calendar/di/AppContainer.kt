package com.example.calendar.di

import android.app.Application
import com.example.calendar.BuildConfig
import com.example.calendar.data.crypto.OpaqueClientImpl
import com.example.calendar.data.crypto.OpaqueParameters
import com.example.calendar.data.local.CalendarDatabase
import com.example.calendar.data.repository.SessionRepositoryImpl
import com.example.calendar.data.repository.EventRepositoryImpl
import com.example.calendar.data.repository.LocalAuthRepositoryImpl
import com.example.calendar.data.repository.AndroidBee2KeyRepository
import com.example.calendar.crypto.Bee2Crypto
import com.example.calendar.domain.repository.CryptoKeyRepository
import com.example.calendar.data.remote.auth.ServerSessionManager
import com.example.calendar.data.remote.auth.AuthApi
import com.example.calendar.data.remote.comparison.ComparisonApi
import com.example.calendar.data.remote.websocket.OkHttpWebSocketTransport
import com.example.calendar.data.remote.websocket.WebSocketTransport
import com.example.calendar.data.repository.ServerAuthRepositoryImpl
import com.example.calendar.data.repository.ComparisonRepositoryImpl
import com.example.calendar.domain.repository.EventRepository
import com.example.calendar.domain.repository.ComparisonRepository
import com.example.calendar.domain.repository.LocalAuthRepository
import com.example.calendar.domain.repository.SessionRepository
import java.net.URI

class AppContainer(application: Application) {

    private val database = CalendarDatabase.getInstance(application)
    val localUserDao = database.localUserDao()

    val serverSessionManager = ServerSessionManager()
    val webSocketTransport: WebSocketTransport = OkHttpWebSocketTransport(
        allowInsecureForDebug = BuildConfig.DEBUG
    )
    val sessionRepository: SessionRepository = SessionRepositoryImpl {
        serverSessionManager.clear()
        webSocketTransport.disconnect()
    }

    val eventRepository: EventRepository =
        EventRepositoryImpl(
            eventDao = database.eventDao(),
            sessions = sessionRepository
        )

    val cryptoKeyRepository: CryptoKeyRepository = AndroidBee2KeyRepository(
        keys = database.bee2KeyDao(),
        bee2 = Bee2Crypto()
    )

    val localAuthRepository: LocalAuthRepository = LocalAuthRepositoryImpl(
        users = localUserDao,
        session = sessionRepository
    )

    val serverAuthRepository = ServerAuthRepositoryImpl(
        opaqueClient = OpaqueClientImpl(
            context = OpaqueParameters.context.copyOf(),
            serverIdentifier = OpaqueParameters.serverIdentifier.copyOf()
        ),
        authApi = AuthApi(BuildConfig.SERVER_BASE_URL),
        serverSessionManager = serverSessionManager,
        webSocketTransport = webSocketTransport,
        webSocketUrl = websocketUrl(BuildConfig.SERVER_BASE_URL),
        localUsers = localUserDao
    )

    val comparisonRepository: ComparisonRepository = ComparisonRepositoryImpl(
        api = ComparisonApi(BuildConfig.SERVER_BASE_URL),
        sessions = sessionRepository,
        serverSession = serverSessionManager,
        webSocket = webSocketTransport
    )

    private fun websocketUrl(baseUrl: String): String {
        val parsed = URI(baseUrl)
        val scheme = when (parsed.scheme.lowercase()) {
            "https" -> "wss"
            "http" -> "ws"
            else -> error("Server URL must use HTTP(S)")
        }
        return URI(scheme, null, parsed.host, parsed.port, "/ws", null, null).toString()
    }
}
