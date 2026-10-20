package com.example.calendar

import com.example.calendar.data.remote.auth.ServerSessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerSessionManagerTest {
    @Test fun tokenIsBoundToLocalOwnerAndClearedOnExpiry() {
        val sessions = ServerSessionManager(tokenLifetimeMillis = 100)
        sessions.install(8, "opaque-token")
        assertEquals("opaque-token", sessions.tokenFor(8))
        assertNull(sessions.tokenFor(9))
        Thread.sleep(150)
        assertNull(sessions.tokenFor(8))
        assertNull(sessions.ownerIdOrNull())
    }
}
