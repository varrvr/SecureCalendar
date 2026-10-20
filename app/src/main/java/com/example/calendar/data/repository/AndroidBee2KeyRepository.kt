package com.example.calendar.data.repository

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.calendar.crypto.Bee2Crypto
import com.example.calendar.data.local.crypto.Bee2KeyDao
import com.example.calendar.data.local.crypto.Bee2KeyEntity
import com.example.calendar.domain.repository.CryptoKeyRepository
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Stores BIGN private keys encrypted by a non-exportable, device-bound Android Keystore key. */
class AndroidBee2KeyRepository(
    private val keys: Bee2KeyDao,
    private val bee2: Bee2Crypto = Bee2Crypto()
) : CryptoKeyRepository {
    private val mutex = Mutex()

    override fun observeFingerprint(ownerId: Long) =
        keys.observe(ownerId).map { it?.fingerprint }

    override suspend fun createKey(ownerId: Long): String = withContext(Dispatchers.IO) {
        require(ownerId > 0)
        mutex.withLock {
            keys.get(ownerId)?.let { return@withLock it.fingerprint }
            val pair = bee2.generateKeyPair()
            var cipherText: ByteArray? = null
            var iv: ByteArray? = null
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateWrappingKey(ownerId))
                cipher.updateAAD(aad(ownerId))
                cipherText = cipher.doFinal(pair.privateKey)
                iv = cipher.iv
                val fingerprint = fingerprint(bee2.hash(pair.publicKey))
                keys.insert(
                    Bee2KeyEntity(
                        ownerId = ownerId,
                        publicKey = pair.publicKey.copyOf(),
                        encryptedPrivateKey = cipherText.copyOf(),
                        iv = iv.copyOf(),
                        fingerprint = fingerprint
                    )
                )
                fingerprint
            } finally {
                bee2.clearSecret(pair.privateKey)
                cipherText?.fill(0)
                iv?.fill(0)
                pair.publicKey.fill(0)
            }
        }
    }

    override suspend fun publicKey(ownerId: Long): ByteArray? = withContext(Dispatchers.IO) {
        keys.get(ownerId)?.publicKey?.copyOf()
    }

    override suspend fun sign(ownerId: Long, message: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        val record = requireNotNull(keys.get(ownerId)) { "Bee2 key has not been created" }
        val privateKey = decrypt(ownerId, record)
        try {
            bee2.sign(message, privateKey)
        } finally {
            bee2.clearSecret(privateKey)
        }
    }

    override suspend fun verify(ownerId: Long, message: ByteArray, signature: ByteArray): Boolean = withContext(Dispatchers.IO) {
        val record = keys.get(ownerId) ?: return@withContext false
        bee2.verify(message, signature, record.publicKey)
    }

    override suspend fun deleteKey(ownerId: Long) = withContext(Dispatchers.IO) {
        keys.delete(ownerId)
        val store = keyStore()
        if (store.containsAlias(alias(ownerId))) store.deleteEntry(alias(ownerId))
    }

    private fun decrypt(ownerId: Long, record: Bee2KeyEntity): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, requireWrappingKey(ownerId), GCMParameterSpec(128, record.iv))
        cipher.updateAAD(aad(ownerId))
        val secret = cipher.doFinal(record.encryptedPrivateKey)
        if (!bee2.validateScalar(secret)) {
            secret.fill(0)
            error("Stored Bee2 private key is invalid")
        }
        return secret
    }

    private fun getOrCreateWrappingKey(ownerId: Long): SecretKey {
        val store = keyStore()
        val name = alias(ownerId)
        (store.getKey(name, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                name,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private fun requireWrappingKey(ownerId: Long): SecretKey =
        requireNotNull(keyStore().getKey(alias(ownerId), null) as? SecretKey) {
            "Device encryption key is unavailable; this Bee2 key cannot be restored on this device"
        }
    private fun alias(ownerId: Long) = "secure_calendar_bee2_wrap_v1_$ownerId"
    private fun aad(ownerId: Long) = "secure-calendar/bee2/v1/user/$ownerId".toByteArray(Charsets.UTF_8)

    private fun fingerprint(hash: ByteArray): String = try {
        hash.take(FINGERPRINT_BYTES).joinToString("") { "%02X".format(it.toInt() and 0xFF) }
    } finally {
        hash.fill(0)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FINGERPRINT_BYTES = 16
    }
}
