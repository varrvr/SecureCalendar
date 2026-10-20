package com.example.calendar.domain.repository

import kotlinx.coroutines.flow.Flow

interface CryptoKeyRepository {
    fun observeFingerprint(ownerId: Long): Flow<String?>
    suspend fun createKey(ownerId: Long): String
    suspend fun publicKey(ownerId: Long): ByteArray?
    suspend fun sign(ownerId: Long, message: ByteArray): ByteArray
    suspend fun verify(ownerId: Long, message: ByteArray, signature: ByteArray): Boolean
    suspend fun deleteKey(ownerId: Long)
}
