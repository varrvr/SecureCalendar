package com.example.calendar

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import com.example.calendar.crypto.Bee2Crypto
import com.example.calendar.data.local.CalendarDatabase
import com.example.calendar.data.local.auth.LocalUserEntity
import com.example.calendar.data.repository.AndroidBee2KeyRepository
import kotlinx.coroutines.runBlocking
import androidx.room.Room
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the actual JNI/Bee2 library; skipped on ABIs for which the project ships no .so. */
@RunWith(AndroidJUnit4::class)
class Bee2NativeInstrumentedTest {
    @Test
    fun nativeHashKeysPointMultiplicationAndBignSignatureWork() {
        assumeTrue("Bee2 JNI is packaged only for arm64-v8a", Build.SUPPORTED_ABIS.contains("arm64-v8a"))
        val bee2 = Bee2Crypto()
        val sample = "secure-calendar-native-smoke".toByteArray()
        val digest = bee2.hash(sample)
        assertEquals(32, digest.size)

        val keyPair = bee2.generateKeyPair()
        val shared = bee2.multiplyPoint(keyPair.publicKey, keyPair.privateKey)
        try {
            assertTrue(bee2.validateScalar(keyPair.privateKey))
            assertTrue(bee2.validatePoint(keyPair.publicKey))
            assertTrue(bee2.validatePoint(shared))
            val signature = bee2.sign(sample, keyPair.privateKey)
            try {
                assertTrue(bee2.verify(sample, signature, keyPair.publicKey))
                assertFalse(bee2.verify(sample + byteArrayOf(0), signature, keyPair.publicKey))
                assertFalse(bee2.verify(sample, signature.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() }, keyPair.publicKey))
            } finally {
                signature.fill(0)
            }
            assertFalse(bee2.validatePoint(ByteArray(64)))
            assertFalse(bee2.validateScalar(ByteArray(32)))
        } finally {
            bee2.clearSecret(keyPair.privateKey)
            keyPair.publicKey.fill(0)
            shared.fill(0)
            digest.fill(0)
        }
    }

    @Test
    fun keystoreWrappedKeyCanSignAndIsRemovedForOneAccount() = runBlocking {
        assumeTrue("Bee2 JNI is packaged only for arm64-v8a", Build.SUPPORTED_ABIS.contains("arm64-v8a"))
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CalendarDatabase::class.java).build()
        try {
            val ownerId = database.localUserDao().insert(
                LocalUserEntity(username = "bee2-${System.nanoTime()}", displayName = "Native test")
            )
            val repository = AndroidBee2KeyRepository(database.bee2KeyDao())
            val fingerprint = repository.createKey(ownerId)
            assertEquals(32, fingerprint.length)
            val challenge = "signed by account $ownerId".toByteArray()
            val signature = repository.sign(ownerId, challenge)
            try {
                assertTrue(repository.verify(ownerId, challenge, signature))
                assertFalse(repository.verify(ownerId, challenge + byteArrayOf(1), signature))
            } finally {
                signature.fill(0)
            }
            assertTrue(database.bee2KeyDao().get(ownerId)?.encryptedPrivateKey?.isNotEmpty() == true)
            repository.deleteKey(ownerId)
            assertTrue(database.bee2KeyDao().get(ownerId) == null)
        } finally {
            database.close()
        }
    }
}
