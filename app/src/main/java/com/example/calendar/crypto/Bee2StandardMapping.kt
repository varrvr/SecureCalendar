package com.example.calendar.crypto

/**
 * Exact fixed-input mapping from STB 34.101.66, section 6.2.3.
 * This is deliberately NOT a HashToCurve implementation: it accepts exactly 32 bytes,
 * has no domain-separated random-oracle suite, and must not silently enable PSI.
 */
class Bee2StandardMapping {
    fun mapPrehashed32(message: ByteArray): ByteArray {
        require(message.size == 32) { "BAKE SWU requires exactly 32 bytes" }
        return Bee2MappingProvider.mapPrehashed32(message).also { point ->
            check(point.size == 64 && validatePoint(point)) { "Invalid Bee2 BAKE mapping output" }
        }
    }
    fun validatePoint(point: ByteArray): Boolean =
        point.size == 64 && Bee2MappingProvider.validatePoint(point)
}

internal object Bee2MappingProvider {
    init { System.loadLibrary("bee2mapping") }
    external fun hashUniform96(uniform: ByteArray): ByteArray
    external fun mapPrehashed32(message: ByteArray): ByteArray
    external fun validatePoint(point: ByteArray): Boolean
    external fun pointMultiply(point: ByteArray, scalar: ByteArray): ByteArray
    external fun pointUnblind(point: ByteArray, scalar: ByteArray): ByteArray
}
