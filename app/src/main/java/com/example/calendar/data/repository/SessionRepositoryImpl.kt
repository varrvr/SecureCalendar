package com.example.calendar.data.repository

import com.example.calendar.domain.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionRepositoryImpl(private val onSessionInvalidated: () -> Unit = {}) : SessionRepository {

    private val _activeUserId = MutableStateFlow<Long?>(null)

    override val activeUserId: StateFlow<Long?> =
        _activeUserId.asStateFlow()

    override fun setActiveUser(userId: Long) {
        require(userId > 0) {
            "Некорректный идентификатор пользователя"
        }

        if (_activeUserId.value != userId) onSessionInvalidated()
        _activeUserId.value = userId
    }

    override fun clearSession() {
        onSessionInvalidated()
        _activeUserId.value = null
    }
}
