package com.example.calendar.data.crypto

/** Values must stay byte-for-byte equal to server/opaque_auth.py. */
object OpaqueParameters {
    val context: ByteArray = "com.example.calendar/opaque/v1".toByteArray(Charsets.UTF_8)
    val serverIdentifier: ByteArray = "secure-calendar-python-server/v1".toByteArray(Charsets.UTF_8)
}
