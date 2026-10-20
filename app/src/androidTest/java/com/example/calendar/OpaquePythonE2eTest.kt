package com.example.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.calendar.data.crypto.OpaqueClientImpl
import com.example.calendar.data.crypto.OpaqueParameters
import com.example.calendar.data.remote.auth.AuthApi
import com.example.calendar.data.remote.auth.ServerSessionManager
import com.example.calendar.data.repository.ServerAuthRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Requires the local Python server on the host at 127.0.0.1:8000 (emulator: 10.0.2.2). */
@RunWith(AndroidJUnit4::class)
class OpaquePythonE2eTest {
    @Test
    fun repositoryInstallsAccountBoundAccessTokenAfterOpaqueRegistrationAndLogin() = runBlocking {
        val username = "repo_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val password = "repo-e2e-${UUID.randomUUID()}"
        val manager = ServerSessionManager()
        val repo = ServerAuthRepositoryImpl(
            OpaqueClientImpl(OpaqueParameters.context.copyOf(), OpaqueParameters.serverIdentifier.copyOf()),
            AuthApi("http://10.0.2.2:8000"),
            manager
        )

        val registered = repo.register(username, "Repository E2E", password, "test-public-key", 41L)
        assertEquals(username, registered.username)
        assertEquals(registered.accessToken, manager.tokenFor(41L))
        assertNull(manager.tokenFor(42L))

        manager.clear()
        val loggedIn = repo.login(username, password, 41L)
        assertEquals(username, loggedIn.username)
        assertEquals(loggedIn.accessToken, manager.tokenFor(41L))
        manager.clear()
        assertNull(manager.tokenFor(41L))
    }

    @Test
    fun androidOpaqueRegistrationLoginAndWrongPasswordAgainstPython() = runBlocking {
        val username = "e2e_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val password = "calendar-e2e-${UUID.randomUUID()}"
        val api = AuthApi("http://10.0.2.2:8000")

        // Values are byte-for-byte paired with server/opaque_auth.py and the
        // vendored P-256 opaque-snake suite.
        val client = OpaqueClientImpl(
            context = OpaqueParameters.context,
            serverIdentifier = OpaqueParameters.serverIdentifier
        )
        val start = client.registrationStart(password.toByteArray(Charsets.UTF_8))
        val response = api.registrationStart(
            username = username,
            displayName = "E2E $username",
            publicKey = "instrumentation-test-public-key",
            message = start.message
        )
        val upload = client.registrationFinish(
            username, password.toByteArray(Charsets.UTF_8), start.state, response
        )
        val registered = api.registrationFinish(username, upload)
        assertEqualsUsername(username, registered.username)
        assertTrue(registered.accessToken.isNotBlank())

        val loginStart = client.loginStart(password.toByteArray(Charsets.UTF_8))
        val challenge = api.loginStart(username, loginStart.message)
        val finalization = client.loginFinish(
            username,
            password.toByteArray(Charsets.UTF_8),
            loginStart.state,
            challenge.credentialResponse
        )
        val loggedIn = api.loginFinish(username, challenge.loginStateId, finalization)
        assertEqualsUsername(username, loggedIn.username)
        assertEquals(registered.userId, loggedIn.userId)
        assertTrue(loggedIn.accessToken.isNotBlank())

        val failedStart = client.loginStart("wrong-password".toByteArray(Charsets.UTF_8))
        val failedChallenge = api.loginStart(username, failedStart.message)
        val rejected = runCatching {
            client.loginFinish(
                username,
                "wrong-password".toByteArray(Charsets.UTF_8),
                failedStart.state,
                failedChallenge.credentialResponse
            )
        }
        assertTrue("wrong password must fail locally before login finish", rejected.isFailure)
    }

    private fun assertEqualsUsername(expected: String, actual: String) = assertEquals(expected, actual)
}
