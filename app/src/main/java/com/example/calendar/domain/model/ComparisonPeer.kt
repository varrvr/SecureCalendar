package com.example.calendar.domain.model

data class ComparisonPeer(val id: Long, val username: String, val displayName: String)

data class ComparisonInvite(
    val sessionId: String,
    val initiatorId: Long,
    val responderId: Long,
    val status: String,
    val periodStartEpochSeconds: Long?,
    val periodEndEpochSeconds: Long?,
    val initiatorUsername: String? = null
)

data class ComparisonSignal(val type: String, val sessionId: String, val senderId: Long)
