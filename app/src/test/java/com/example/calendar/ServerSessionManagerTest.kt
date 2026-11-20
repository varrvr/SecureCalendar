package com.example.calendar

import com.example.calendar.data.remote.auth.ServerSessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
    @Test fun serverAndLocalIdentifiersAreIndependent() {
        val sessions = ServerSessionManager()
        sessions.install(700, "token", serverUserId = 2)
        assertEquals(2L, sessions.serverUserIdFor(700))
        assertNull(sessions.serverUserIdFor(2))
    }
    @Test fun staleAuthenticationCannotInstallAfterLogoutOrEndpointChange() {
        val sessions = ServerSessionManager()
        val generation = sessions.generation()
        sessions.clear()
        assertThrows(IllegalStateException::class.java) {
            sessions.install(1, "token", expectedGeneration = generation)
        }
        assertNull(sessions.tokenFor(1))
    }
}
