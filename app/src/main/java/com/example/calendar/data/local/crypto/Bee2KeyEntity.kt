package com.example.calendar.data.local.crypto

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.example.calendar.data.local.auth.LocalUserEntity

/** Public key plus AES-GCM wrapped private key. Plaintext private keys are never persisted. */
@Entity(
    tableName = "bee2_keys",
    foreignKeys = [
        ForeignKey(
            entity = LocalUserEntity::class,
            parentColumns = ["id"],
            childColumns = ["ownerId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Bee2KeyEntity(
    @PrimaryKey val ownerId: Long,
    val publicKey: ByteArray,
    val encryptedPrivateKey: ByteArray,
    val iv: ByteArray,
    val fingerprint: String
)
