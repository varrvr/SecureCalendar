
package com.example.calendar.crypto

interface PsiCrypto {

    fun hash(data: ByteArray): ByteArray

    fun generateScalar(): ByteArray

    fun validatePoint(point: ByteArray): Boolean

    fun hashToCurve(data: ByteArray, domainSeparationTag: ByteArray): ByteArray

    fun multiplyPoint(
        point: ByteArray,
        scalar: ByteArray
    ): ByteArray
}

/** Adapter for the available Bee2 primitives plus the separately agreed hash-to-curve suite. */
class Bee2PsiCrypto(
    private val bee2: Bee2Crypto,
    private val hashToCurveProvider: HashToCurve,
    private val psiPointValidator: PsiPointValidator
) : PsiCrypto {
    override fun hash(data: ByteArray): ByteArray = bee2.hash(data)
    override fun generateScalar(): ByteArray = bee2.generateScalar()
    override fun validatePoint(point: ByteArray): Boolean =
        point.size == Bee2Provider.POINT_SIZE && psiPointValidator.isValid(point)
    override fun hashToCurve(data: ByteArray, domainSeparationTag: ByteArray): ByteArray {
        require(domainSeparationTag.isNotEmpty()) { "Hash-to-curve domain separation is required" }
        val point = hashToCurveProvider.map(data, domainSeparationTag)
        require(validatePoint(point)) { "Hash-to-curve returned a point outside the agreed PSI group" }
        return point
    }
    override fun multiplyPoint(point: ByteArray, scalar: ByteArray): ByteArray =
        bee2.multiplyPoint(point, scalar)
}
