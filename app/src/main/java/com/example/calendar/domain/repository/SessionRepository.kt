package com.example.calendar.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface SessionRepository {

    val activeUserId: StateFlow<Long?>

    fun setActiveUser(userId: Long)

    fun clearSession()
}