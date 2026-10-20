package com.example.calendar.data.crypto

data class OpaqueStartResult(
    val message: ByteArray,
    val state: ByteArray
)

interface OpaqueClient {

    fun registrationStart(
        password: ByteArray
    ): OpaqueStartResult

    fun registrationFinish(
        username: String,
        password: ByteArray,
        state: ByteArray,
        serverResponse: ByteArray
    ): ByteArray

    fun loginStart(
        password: ByteArray
    ): OpaqueStartResult

    fun loginFinish(
        username: String,
        password: ByteArray,
        state: ByteArray,
        serverResponse: ByteArray
    ): ByteArray
}
