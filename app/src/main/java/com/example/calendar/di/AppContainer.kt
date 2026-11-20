package com.example.calendar.di

import android.app.Application
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
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
import com.example.calendar.data.remote.ServerEndpointSettings
import com.example.calendar.data.remote.websocket.OkHttpWebSocketTransport
import com.example.calendar.data.remote.websocket.WebSocketTransport
import com.example.calendar.data.repository.ServerAuthRepositoryImpl
import com.example.calendar.data.repository.ComparisonRepositoryImpl
import com.example.calendar.domain.repository.EventRepository
import com.example.calendar.domain.repository.ComparisonRepository
import com.example.calendar.domain.repository.LocalAuthRepository
import com.example.calendar.domain.repository.SessionRepository

class AppContainer(application: Application) {

    val serverEndpointSettings = ServerEndpointSettings(application, BuildConfig.SERVER_BASE_URL)

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
        authApi = AuthApi(serverEndpointSettings::current),
        serverSessionManager = serverSessionManager,
        webSocketTransport = webSocketTransport,
        webSocketUrl = serverEndpointSettings::webSocketUrl,
        localUsers = localUserDao,
        activeLocalOwner = { sessionRepository.activeUserId.value }
    )

    init {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default).launchExpiryGuard()
    }
    private fun kotlinx.coroutines.CoroutineScope.launchExpiryGuard() = launch {
        while (isActive) {
            kotlinx.coroutines.delay(1000)
            if (serverSessionManager.ownerIdOrNull() == null &&
                webSocketTransport.state.value != com.example.calendar.data.remote.websocket.WebSocketState.DISCONNECTED) {
                webSocketTransport.disconnect()
            }
        }
    }

    val comparisonRepository: ComparisonRepository = ComparisonRepositoryImpl(
        api = ComparisonApi(serverEndpointSettings::current),
        sessions = sessionRepository,
        serverSession = serverSessionManager,
        webSocket = webSocketTransport
    )
}
