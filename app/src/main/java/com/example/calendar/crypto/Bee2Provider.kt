
package com.example.calendar.crypto

object Bee2Provider {

    const val HASH_SIZE = 32
    const val SCALAR_SIZE = 32
    const val POINT_SIZE = 64
    const val SIGNATURE_SIZE = 48

    const val CURVE_OID =
        "1.2.112.0.2.0.34.101.45.3.1"

    init {
        System.loadLibrary("bee2bridge")
    }

    external fun beltHash(
        data: ByteArray
    ): ByteArray

    external fun validateScalar(
        scalar: ByteArray
    ): Boolean

    external fun validatePoint(
        point: ByteArray
    ): Boolean

    external fun publicFromScalar(
        scalar: ByteArray
    ): ByteArray

    external fun multiplyPoint(
        point: ByteArray,
        scalar: ByteArray
    ): ByteArray

    external fun bignSignMessage(
        message: ByteArray,
        privateKey: ByteArray
    ): ByteArray

    external fun bignVerifyMessage(
        message: ByteArray,
        signature: ByteArray,
        publicKey: ByteArray
    ): Boolean
}
