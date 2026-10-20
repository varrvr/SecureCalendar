package com.example.calendar.data.crypto

import se.digg.opaque_ke_uniffi.clientRegistrationStart
import se.digg.opaque_ke_uniffi.clientRegistrationFinish
import se.digg.opaque_ke_uniffi.clientLoginStart
import se.digg.opaque_ke_uniffi.clientLoginFinish

class OpaqueClientImpl(
    private val context: ByteArray,
    private val clientIdentifier: ByteArray,
    private val serverIdentifier: ByteArray
) : OpaqueClient {

    override fun registrationStart(
        password: ByteArray
    ): OpaqueStartResult {

        val result = clientRegistrationStart(password)

        return OpaqueStartResult(
            message = result.registrationRequest,
            state = result.clientRegistration
        )
    }

    override fun registrationFinish(
        password: ByteArray,
        state: ByteArray,
        serverResponse: ByteArray
    ): ByteArray {

        val result = clientRegistrationFinish(
            password = password,
            clientRegistration = state,
            registrationResponse = serverResponse,
            clientIdentifier = clientIdentifier,
            serverIdentifier = serverIdentifier
        )

        return result.registrationUpload
    }

    override fun loginStart(
        password: ByteArray
    ): OpaqueStartResult {

        val result = clientLoginStart(password)

        return OpaqueStartResult(
            message = result.credentialRequest,
            state = result.clientRegistration
        )
    }

    override fun loginFinish(
        password: ByteArray,
        state: ByteArray,
        serverResponse: ByteArray
    ): ByteArray {

        val result = clientLoginFinish(
            credentialResponse = serverResponse,
            clientRegistration = state,
            password = password,
            context = context,
            clientIdentifier = clientIdentifier,
            serverIdentifier = serverIdentifier
        )

        return result.credentialFinalization
    }
}