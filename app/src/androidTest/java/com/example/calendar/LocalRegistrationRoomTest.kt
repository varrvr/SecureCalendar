package com.example.calendar

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.calendar.data.local.CalendarDatabase
import com.example.calendar.data.repository.LocalAuthRepositoryImpl
import com.example.calendar.data.repository.SessionRepositoryImpl
import com.example.calendar.domain.model.LocalAuthResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalRegistrationRoomTest {
    @Test fun registrationWritesCredentialAndCreatesLocalSession() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).build()
        try {
            val sessions = SessionRepositoryImpl()
            val repository = LocalAuthRepositoryImpl(database.localUserDao(), sessions)
            val result = repository.register("Test User", "roomtest", "correct horse battery".toCharArray())
            assertTrue("Registration failed: $result", result is LocalAuthResult.Success)
            assertTrue(sessions.activeUserId.value != null)
            val stored = database.localUserDao().getByUsername("roomtest")
            assertTrue(stored?.passwordSalt?.isNotEmpty() == true)
            assertTrue(stored?.passwordVerifier?.isNotEmpty() == true)
        } finally {
            database.close()
        }
    }
}
