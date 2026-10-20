package com.example.calendar.domain.model

data class LocalAccount(val id: Long, val username: String, val displayName: String, val serverRegistrationStatus: ServerRegistrationStatus)

enum class ServerRegistrationStatus { NOT_REGISTERED, REGISTERED, UNKNOWN }

sealed interface LocalAuthResult {
    data class Success(val account: LocalAccount) : LocalAuthResult
    data object InvalidCredentials : LocalAuthResult
    data object UsernameTaken : LocalAuthResult
    data class Failure(val message: String) : LocalAuthResult
}
