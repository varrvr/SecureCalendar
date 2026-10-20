package com.example.calendar

import com.example.calendar.data.local.auth.LocalUserDao
import com.example.calendar.data.local.auth.LocalUserEntity
import com.example.calendar.data.repository.LocalAuthRepositoryImpl
import com.example.calendar.data.repository.SessionRepositoryImpl
import com.example.calendar.domain.model.LocalAuthResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAuthRepositoryTest {
    @Test fun registerLoginAndRejectWrongPassword() = runBlocking {
        val dao = MemoryUsers()
        val session = SessionRepositoryImpl()
        val repository = LocalAuthRepositoryImpl(dao, session)
        val registered = repository.register("Calendar User", "alice", "correct horse".toCharArray())
        assertTrue(registered is LocalAuthResult.Success)
        val stored = dao.users.single()
        assertTrue(stored.passwordSalt.isNotEmpty())
        assertTrue(stored.passwordVerifier.isNotEmpty())
        assertFalse(stored.passwordVerifier.decodeToString().contains("correct horse"))
        assertEquals(1L, session.activeUserId.value)

        session.clearSession()
        assertTrue(repository.login("ALICE", "wrong password".toCharArray()) is LocalAuthResult.InvalidCredentials)
        assertEquals(null, session.activeUserId.value)
        assertTrue(repository.login("alice", "correct horse".toCharArray()) is LocalAuthResult.Success)
        assertEquals(1L, session.activeUserId.value)
    }

    private class MemoryUsers : LocalUserDao {
        val users = mutableListOf<LocalUserEntity>()
        override fun observeAll(): Flow<List<LocalUserEntity>> = MutableStateFlow(users)
        override suspend fun getById(userId: Long) = users.firstOrNull { it.id == userId }
        override suspend fun getByUsername(username: String) = users.firstOrNull { it.username == username }
        override suspend fun getByUsernameCaseInsensitive(username: String) = users.firstOrNull { it.username.equals(username, true) }
        override suspend fun insert(user: LocalUserEntity): Long {
            val id = (users.maxOfOrNull { it.id } ?: 0L) + 1
            users += user.copy(id = id, passwordSalt = user.passwordSalt.copyOf(), passwordVerifier = user.passwordVerifier.copyOf())
            return id
        }
        override suspend fun update(user: LocalUserEntity) { users.replaceAll { if (it.id == user.id) user else it } }
        override suspend fun updateServerRegistrationStatus(userId: Long, status: String) {
            users.replaceAll { if (it.id == userId) it.copy(serverRegistrationStatus = status) else it }
        }
    }
}
