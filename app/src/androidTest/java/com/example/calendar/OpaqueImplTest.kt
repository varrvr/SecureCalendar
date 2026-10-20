package com.example.calendar

import org.junit.Assert.assertTrue
import org.junit.Test
import se.digg.opaque_ke_uniffi.clientRegistrationStart
import se.digg.opaque_ke_uniffi.clientLoginStart

class OpaqueTest {

    @Test
    fun registrationStartWorks() {
        val password = "test_password".toByteArray()

        val result = clientRegistrationStart(password)

        assertTrue(result.registrationRequest.isNotEmpty())
        assertTrue(result.clientRegistration.isNotEmpty())
    }

    @Test
    fun loginStartWorks() {
        val password = "test_password".toByteArray()

        val result = clientLoginStart(password)

        assertTrue(result.credentialRequest.isNotEmpty())
        assertTrue(result.clientRegistration.isNotEmpty())
    }
}