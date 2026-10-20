package com.example.calendar.domain.repository

import com.example.calendar.domain.model.LocalAuthResult

interface LocalAuthRepository {
    suspend fun register(displayName: String, username: String, password: CharArray): LocalAuthResult
    suspend fun login(username: String, password: CharArray): LocalAuthResult
}
