
package com.example.calendar.crypto

import java.security.SecureRandom

class BignKeyPair(
    val privateKey: ByteArray,
    val publicKey: ByteArray
) {
    override fun toString(): String = "BignKeyPair(privateKey=<redacted>, publicKey=<${publicKey.size} bytes>)"
}

class Bee2Crypto {

    private val random = SecureRandom()

    fun hash(data: ByteArray): ByteArray {
        return Bee2Provider.beltHash(data).also {
            check(it.size == Bee2Provider.HASH_SIZE) { "Unexpected BELT hash length" }
        }
    }

    fun hash(text: String): ByteArray {
        return hash(text.encodeToByteArray())
    }

    fun validateScalar(scalar: ByteArray): Boolean {
        if (scalar.size != Bee2Provider.SCALAR_SIZE) {
            return false
        }

        return Bee2Provider.validateScalar(scalar)
    }

    fun validatePoint(point: ByteArray): Boolean {
        if (point.size != Bee2Provider.POINT_SIZE) {
            return false
        }

        return Bee2Provider.validatePoint(point)
    }

    fun generateScalar(): ByteArray {
        repeat(1024) {
            val candidate = ByteArray(
                Bee2Provider.SCALAR_SIZE
            )

            random.nextBytes(candidate)

            if (validateScalar(candidate)) {
                return candidate
            }

            candidate.fill(0)
        }

        throw IllegalStateException(
            "Unable to generate BIGN scalar"
        )
    }

    fun generateKeyPair(): BignKeyPair {
        val privateKey = generateScalar()

        return try {
            val publicKey = publicFromScalar(privateKey)

            BignKeyPair(
                privateKey = privateKey,
                publicKey = publicKey
            )
        } catch (error: Throwable) {
            privateKey.fill(0)
            throw error
        }
    }

    fun publicFromScalar(
        scalar: ByteArray
    ): ByteArray {
        require(validateScalar(scalar)) {
            "Invalid BIGN scalar"
        }

        val output = Bee2Provider.publicFromScalar(scalar)
        if (output.size != Bee2Provider.POINT_SIZE || !validatePoint(output)) {
            output.fill(0)
            error("Bee2 returned an invalid public key")
        }
        return output
    }

    fun multiplyPoint(
        point: ByteArray,
        scalar: ByteArray
    ): ByteArray {
        require(validateScalar(scalar)) {
            "Invalid BIGN scalar"
        }

        require(validatePoint(point)) {
            "Invalid BIGN point"
        }

        val output = Bee2Provider.multiplyPoint(
            point,
            scalar
        )
        if (output.size != Bee2Provider.POINT_SIZE || !validatePoint(output)) {
            output.fill(0)
            error("Bee2 returned an invalid multiplied point")
        }
        return output
    }

    fun sign(
        message: ByteArray,
        privateKey: ByteArray
    ): ByteArray {
        require(validateScalar(privateKey)) {
            "Invalid signing private key"
        }

        val signature = Bee2Provider.bignSignMessage(
            message,
            privateKey
        )
        if (signature.size != Bee2Provider.SIGNATURE_SIZE) {
            signature.fill(0)
            error("Bee2 returned an invalid signature length")
        }
        return signature
    }

    fun verify(
        message: ByteArray,
        signature: ByteArray,
        publicKey: ByteArray
    ): Boolean {
        if (signature.size != Bee2Provider.SIGNATURE_SIZE) {
            return false
        }

        if (!validatePoint(publicKey)) {
            return false
        }

        return Bee2Provider.bignVerifyMessage(
            message,
            signature,
            publicKey
        )
    }

    fun clearSecret(secret: ByteArray) {
        secret.fill(0)
    }
}
