package com.example.calendar.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.example.calendar.data.local.auth.LocalUserDao
import com.example.calendar.data.local.auth.LocalUserEntity
import com.example.calendar.domain.model.LocalAccount
import com.example.calendar.domain.model.LocalAuthResult
import com.example.calendar.domain.model.ServerRegistrationStatus
import com.example.calendar.domain.repository.LocalAuthRepository
import com.example.calendar.domain.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class LocalAuthRepositoryImpl(
    private val users: LocalUserDao,
    private val session: SessionRepository
) : LocalAuthRepository {
    private data class Failures(var count: Int = 0, var blockedUntil: Long = 0)
    private val attempts = ConcurrentHashMap<String, Failures>()

    override suspend fun register(displayName: String, username: String, password: CharArray): LocalAuthResult = withContext(Dispatchers.IO) {
        val normalized = username.trim().lowercase()
        val name = displayName.trim()
        if (name.isEmpty() || normalized.length !in 3..64 || password.size !in 8..1024) {
            password.fill('\u0000')
            return@withContext LocalAuthResult.Failure("Имя, логин или пароль не соответствуют требованиям")
        }
        val salt = PasswordHasher.newSalt()
        val verifier = try { PasswordHasher.derive(password, salt) } finally { password.fill('\u0000') }
        try {
            val id = users.insert(LocalUserEntity(
                username = normalized,
                displayName = name,
                passwordKdf = PasswordHasher.ALGORITHM,
                passwordSalt = salt,
                passwordVerifier = verifier,
                passwordIterations = PasswordHasher.ITERATIONS
            ))
            session.setActiveUser(id)
            LocalAuthResult.Success(LocalAccount(id, normalized, name, ServerRegistrationStatus.NOT_REGISTERED))
        } catch (_: SQLiteConstraintException) {
            LocalAuthResult.UsernameTaken
        } finally {
            salt.fill(0); verifier.fill(0)
        }
    }

    override suspend fun login(username: String, password: CharArray): LocalAuthResult = withContext(Dispatchers.IO) {
        val normalized = username.trim().lowercase()
        if (password.size > 1024) {
            password.fill('\u0000')
            return@withContext LocalAuthResult.InvalidCredentials
        }
        val now = System.currentTimeMillis()
        val failure = attempts.computeIfAbsent(normalized) { Failures() }
        if (now < failure.blockedUntil) {
            password.fill('\u0000')
            return@withContext LocalAuthResult.Failure("Слишком много попыток. Повторите позже")
        }
        val user = try {
            users.getByUsername(normalized)
        } catch (error: Exception) {
            password.fill('\u0000')
            throw error
        }
        val valid = try {
            user != null && user.passwordKdf == PasswordHasher.ALGORITHM && user.passwordSalt.isNotEmpty() &&
                PasswordHasher.verify(password, user.passwordSalt, user.passwordIterations, user.passwordVerifier)
        } finally { password.fill('\u0000') }
        if (!valid || user == null) {
            failure.count++
            if (failure.count >= 5) { failure.count = 0; failure.blockedUntil = now + 30_000 }
            return@withContext LocalAuthResult.InvalidCredentials
        }
        attempts.remove(normalized)
        session.setActiveUser(user.id)
        LocalAuthResult.Success(user.toAccount())
    }

    private fun LocalUserEntity.toAccount() = LocalAccount(
        id, username, displayName,
        when (serverRegistrationStatus) {
            "REGISTERED" -> ServerRegistrationStatus.REGISTERED
            "UNKNOWN" -> ServerRegistrationStatus.UNKNOWN
            else -> ServerRegistrationStatus.NOT_REGISTERED
        }
    )
}
