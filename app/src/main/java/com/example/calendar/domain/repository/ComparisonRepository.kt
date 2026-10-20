package com.example.calendar.domain.repository

import com.example.calendar.domain.model.ComparisonInvite
import com.example.calendar.domain.model.ComparisonPeer
import com.example.calendar.domain.model.ComparisonSignal
import kotlinx.coroutines.flow.Flow

interface ComparisonRepository {
    val connected: Flow<Boolean>
    val signals: Flow<ComparisonSignal>
    suspend fun searchUsers(query: String): List<ComparisonPeer>
    suspend fun createRequest(username: String, startEpochSeconds: Long, endEpochSeconds: Long): ComparisonInvite
    suspend fun incomingRequests(): List<ComparisonInvite>
    suspend fun accept(sessionId: String): ComparisonInvite
    suspend fun reject(sessionId: String): ComparisonInvite
    suspend fun cancel(sessionId: String): ComparisonInvite
}
