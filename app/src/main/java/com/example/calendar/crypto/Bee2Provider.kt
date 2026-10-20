
package com.example.calendar.crypto

internal object Bee2Provider {

    const val HASH_SIZE = 32
    const val SCALAR_SIZE = 32
    const val POINT_SIZE = 64
    const val SIGNATURE_SIZE = 48

    const val CURVE_OID =
        "1.2.112.0.2.0.34.101.45.3.1"

    init {
        System.loadLibrary("bee2bridge")
    }

    internal external fun beltHash(
        data: ByteArray
    ): ByteArray

    internal external fun validateScalar(
        scalar: ByteArray
    ): Boolean

    internal external fun validatePoint(
        point: ByteArray
    ): Boolean

    internal external fun publicFromScalar(
        scalar: ByteArray
    ): ByteArray

    internal external fun multiplyPoint(
        point: ByteArray,
        scalar: ByteArray
    ): ByteArray

    internal external fun bignSignMessage(
        message: ByteArray,
        privateKey: ByteArray
    ): ByteArray

    internal external fun bignVerifyMessage(
        message: ByteArray,
        signature: ByteArray,
        publicKey: ByteArray
    ): Boolean
}
