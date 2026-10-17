
package com.example.calendar.crypto

interface PsiCrypto {

    fun hash(data: ByteArray): ByteArray

    fun generateScalar(): ByteArray

    fun validatePoint(point: ByteArray): Boolean

    fun hashToCurve(data: ByteArray): ByteArray

    fun multiplyPoint(
        point: ByteArray,
        scalar: ByteArray
    ): ByteArray
}
