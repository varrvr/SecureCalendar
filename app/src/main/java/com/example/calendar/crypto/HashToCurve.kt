package com.example.calendar.crypto

/** Must be supplied by the agreed Bee2 mapping; no hash-times-generator fallback is provided. */
fun interface HashToCurve {
    fun map(message: ByteArray, domainSeparationTag: ByteArray): ByteArray
}

/** Validation policy required by the agreed PSI curve, including subgroup and identity rules. */
fun interface PsiPointValidator {
    fun isValid(point: ByteArray): Boolean
}
