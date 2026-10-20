package com.example.calendar.data.repository

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

internal object PasswordHasher {
    const val ALGORITHM = "PBKDF2WithHmacSHA256"
    const val ITERATIONS = 310_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private val random = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    fun derive(password: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun verify(password: CharArray, salt: ByteArray, iterations: Int, expected: ByteArray): Boolean {
        val actual = derive(password, salt, iterations)
        return try { MessageDigest.isEqual(actual, expected) } finally { actual.fill(0) }
    }
}
