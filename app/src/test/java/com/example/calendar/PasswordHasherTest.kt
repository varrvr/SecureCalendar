package com.example.calendar

import com.example.calendar.data.repository.PasswordHasher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {
    @Test fun verifierMatchesOnlyOriginalPassword() {
        val password = "a long local passphrase".toCharArray()
        val salt = PasswordHasher.newSalt()
        val verifier = PasswordHasher.derive(password, salt)
        assertTrue(PasswordHasher.verify("a long local passphrase".toCharArray(), salt, PasswordHasher.ITERATIONS, verifier))
        assertFalse(PasswordHasher.verify("wrong password".toCharArray(), salt, PasswordHasher.ITERATIONS, verifier))
        assertNotEquals(verifier.toList(), PasswordHasher.derive("a long local passphrase".toCharArray(), PasswordHasher.newSalt()).toList())
        password.fill('\u0000'); salt.fill(0); verifier.fill(0)
    }
}
