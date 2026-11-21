package com.example.calendar.crypto

/**
 * The prime-order-group arithmetic behind HashedDH OPRF:
 * P -> rP -> k(rP) -> r^-1 k(rP) = kP.
 * Only Bee2 operations are used. This does not define f_k, a hash-to-group suite,
 * finalization, verifiability, set-U consistency or malicious-secure PSI.
 * Caller owns and must wipe the separate per-session scalar buffers.
 */
class Bee2OprfPointOperations {
    fun blind(point: ByteArray, scalar: ByteArray): ByteArray = multiply(point, scalar)
    fun evaluate(blindedPoint: ByteArray, oprfKey: ByteArray): ByteArray = multiply(blindedPoint, oprfKey)
    fun unblind(evaluatedPoint: ByteArray, blind: ByteArray): ByteArray {
        require(evaluatedPoint.size == 64 && blind.size == 32)
        return Bee2MappingProvider.pointUnblind(evaluatedPoint, blind).also { validate(it) }
    }
    private fun multiply(point: ByteArray, scalar: ByteArray): ByteArray {
        require(point.size == 64 && scalar.size == 32)
        return Bee2MappingProvider.pointMultiply(point, scalar).also { validate(it) }
    }
    private fun validate(point: ByteArray) {
        check(point.size == 64 && Bee2MappingProvider.validatePoint(point)) { "Invalid Bee2 group operation output" }
    }
}
