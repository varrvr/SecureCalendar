package com.example.calendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.calendar.crypto.Bee2OprfPointOperations
import com.example.calendar.crypto.Bee2Crypto
import com.example.calendar.crypto.Bee2StandardMapping
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Bee2MappingInstrumentedTest {
    private fun hex(text: String) = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun nativeMappingMatchesOfficialBee2VectorAndExistingCurveEncoding() {
        val mapper = Bee2StandardMapping()
        val message = hex("AD1362A8F9A3D42FBE1B8E6F1C88AAD50F51D91347617C20BD4AB07AEF4F26A1")
        val expected = hex("014417D3355557317D2E2AB6D08754878D19E8D97B71FDC95DBB2A9B894D16D77704A0B5CAA9CDA10791E4760671E1050DDEAB7083A7458447866ADB01473810")
        val actual = mapper.mapPrehashed32(message)
        assertArrayEquals(expected, actual)
        assertTrue(mapper.validatePoint(actual))
        assertTrue(Bee2Crypto().validatePoint(actual))
        assertArrayEquals(actual, mapper.mapPrehashed32(message))
    }
    @Test fun invalidLengthAndInvalidPointsAreRejected() {
        val mapper = Bee2StandardMapping()
        assertFalse(mapper.validatePoint(ByteArray(64)))
        assertFalse(mapper.validatePoint(ByteArray(64) { -1 }))
        assertFalse(mapper.validatePoint(ByteArray(63)))
        for (size in listOf(0, 31, 33)) {
            assertThrows(IllegalArgumentException::class.java) { mapper.mapPrehashed32(ByteArray(size)) }
        }
    }
    @Test fun hashedDhGroupRoundTripUsesRealBee2WithIndependentBlinds() {
        val crypto = Bee2Crypto()
        val ops = Bee2OprfPointOperations()
        val point = Bee2StandardMapping().mapPrehashed32(ByteArray(32) { it.toByte() })
        val key = crypto.generateScalar()
        try {
            val expected = crypto.multiplyPoint(point, key)
            repeat(8) {
                val blind = crypto.generateScalar()
                try {
                    val request = ops.blind(point, blind)
                    val response = ops.evaluate(request, key)
                    assertArrayEquals(expected, ops.unblind(response, blind))
                    // A valid curve point alone is insufficient: a dishonest evaluation changes the result.
                    assertFalse(expected.contentEquals(ops.unblind(request, blind)))
                } finally { blind.fill(0) }
            }
        } finally { key.fill(0) }
    }

    @Test fun groupOperationsRejectInvalidPeerPointsAndScalars() {
        val ops = Bee2OprfPointOperations()
        val point = Bee2StandardMapping().mapPrehashed32(ByteArray(32))
        val one = ByteArray(32).also { it[0] = 1 }
        val order = hex("07663d2699bf5a7efc4dfb0dd68e5cd9ffffffffffffffffffffffffffffffff")
        for (invalid in listOf(ByteArray(32), order, ByteArray(32) { -1 }, ByteArray(31))) {
            assertThrows(IllegalArgumentException::class.java) { ops.blind(point, invalid) }
            assertThrows(IllegalArgumentException::class.java) { ops.unblind(point, invalid) }
        }
        for (invalid in listOf(ByteArray(64), ByteArray(64) { -1 }, ByteArray(63))) {
            assertThrows(IllegalArgumentException::class.java) { ops.evaluate(invalid, one) }
            assertThrows(IllegalArgumentException::class.java) { ops.unblind(invalid, one) }
        }
    }

}
