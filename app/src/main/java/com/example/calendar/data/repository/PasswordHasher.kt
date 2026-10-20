package com.example.calendar.data.repository

import java.security.MessageDigest
import java.security.SecureRandom
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal object PasswordHasher {
    const val ALGORITHM = "PBKDF2WithHmacSHA256"
    const val ITERATIONS = 310_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256
    private val random = SecureRandom()

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    fun derive(password: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        require(iterations > 0)
        val encoder = StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val encoded = encoder.encode(CharBuffer.wrap(password))
        val passwordBytes = ByteArray(encoded.remaining()).also(encoded::get)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(passwordBytes, "HmacSHA256"))
        val output = ByteArray(KEY_BITS / 8)
        val blockInput = ByteArray(salt.size + 4)
        salt.copyInto(blockInput)
        return try {
            var block = 1
            var outputOffset = 0
            while (outputOffset < output.size) {
                blockInput[blockInput.lastIndex - 3] = (block ushr 24).toByte()
                blockInput[blockInput.lastIndex - 2] = (block ushr 16).toByte()
                blockInput[blockInput.lastIndex - 1] = (block ushr 8).toByte()
                blockInput[blockInput.lastIndex] = block.toByte()
                var u = mac.doFinal(blockInput)
                val accumulator = u.copyOf()
                for (round in 1 until iterations) {
                    val next = mac.doFinal(u)
                    u.fill(0)
                    u = next
                    for (index in accumulator.indices) accumulator[index] = (accumulator[index].toInt() xor u[index].toInt()).toByte()
                }
                val copyLength = minOf(accumulator.size, output.size - outputOffset)
                accumulator.copyInto(output, outputOffset, 0, copyLength)
                outputOffset += copyLength
                u.fill(0)
                accumulator.fill(0)
                block++
            }
            output
        } finally {
            passwordBytes.fill(0)
            if (encoded.hasArray()) encoded.array().fill(0)
            blockInput.fill(0)
            mac.reset()
        }
    }

    fun verify(password: CharArray, salt: ByteArray, iterations: Int, expected: ByteArray): Boolean {
        val actual = derive(password, salt, iterations)
        return try { MessageDigest.isEqual(actual, expected) } finally { actual.fill(0) }
    }
}
