package com.example.calendar.di

import android.app.Application
import com.example.calendar.data.local.CalendarDatabase
import com.example.calendar.data.repository.SessionRepositoryImpl
import com.example.calendar.data.repository.EventRepositoryImpl
import com.example.calendar.data.repository.LocalAuthRepositoryImpl
import com.example.calendar.data.remote.auth.ServerSessionManager
import com.example.calendar.data.remote.websocket.OkHttpWebSocketTransport
import com.example.calendar.data.remote.websocket.WebSocketTransport
import com.example.calendar.domain.repository.EventRepository
import com.example.calendar.domain.repository.LocalAuthRepository
import com.example.calendar.domain.repository.SessionRepository

class AppContainer(application: Application) {

    private val database = CalendarDatabase.getInstance(application)

    val serverSessionManager = ServerSessionManager()
    val webSocketTransport: WebSocketTransport = OkHttpWebSocketTransport()
    val sessionRepository: SessionRepository = SessionRepositoryImpl {
        serverSessionManager.clear()
        webSocketTransport.disconnect()
    }

    val eventRepository: EventRepository =
        EventRepositoryImpl(
            eventDao = database.eventDao(),
            sessions = sessionRepository
        )

    val localUserDao = database.localUserDao()

    val localAuthRepository: LocalAuthRepository = LocalAuthRepositoryImpl(
        users = localUserDao,
        session = sessionRepository
    )
}
