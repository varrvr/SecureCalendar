package com.example.calendar.crypto

/** Project profile: RFC 9380 XMD + hash_to_field(count=2,L=48) + SSWU(Z=-11), h=1.
 * BELT hash (32-byte output and input block) and BIGN curve128v1, not an IETF ciphersuite.
 */
class Bee2HashToGroup(private val bee2: Bee2Crypto = Bee2Crypto()) : HashToCurve {
    override fun map(message: ByteArray, domainSeparationTag: ByteArray): ByteArray {
        val uniform = expandXmd(message, domainSeparationTag, 96, bee2::hash)
        return try { Bee2MappingProvider.hashUniform96(uniform) } finally { uniform.fill(0) }
    }
}

/** RFC 9380 section 5.3.1, hash input block size 32 for BELT. */
internal fun expandXmd(message: ByteArray, dst: ByteArray, length: Int,
                       hash: (ByteArray) -> ByteArray, blockSize: Int = 32): ByteArray {
    require(dst.isNotEmpty() && length in 1..8160)
    val domain = if (dst.size > 255) hash("H2C-OVERSIZE-DST-".encodeToByteArray() + dst) else dst
    val suffix = domain + byteArrayOf(domain.size.toByte())
    val b0 = hash(ByteArray(blockSize) + message + byteArrayOf((length ushr 8).toByte(), length.toByte(), 0) + suffix)
    val out = ByteArray(length)
    var previous = ByteArray(32)
    try {
        for (i in 1..((length + 31) / 32)) {
            val input = if (i == 1) b0 else ByteArray(32) { (b0[it].toInt() xor previous[it].toInt()).toByte() }
            val next = hash(input + byteArrayOf(i.toByte()) + suffix)
            check(next.size == 32)
            previous.fill(0); previous = next
            next.copyInto(out, (i - 1) * 32, 0, minOf(32, length - (i - 1) * 32))
        }
        return out
    } finally { b0.fill(0); previous.fill(0) }
}
