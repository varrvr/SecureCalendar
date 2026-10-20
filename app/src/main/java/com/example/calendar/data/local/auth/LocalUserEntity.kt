package com.example.calendar.data.local.auth

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(
    tableName = "local_users",
    indices = [
        Index(value = ["username"], unique = true)
    ]
)
data class LocalUserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val username: String,
    val displayName: String,
    val serverRegistrationStatus: String = "NOT_REGISTERED",
    @ColumnInfo(defaultValue = "'PBKDF2WithHmacSHA256'") val passwordKdf: String = "PBKDF2WithHmacSHA256",
    @ColumnInfo(defaultValue = "X''") val passwordSalt: ByteArray = byteArrayOf(),
    @ColumnInfo(defaultValue = "X''") val passwordVerifier: ByteArray = byteArrayOf(),
    @ColumnInfo(defaultValue = "310000") val passwordIterations: Int = 310_000
)
