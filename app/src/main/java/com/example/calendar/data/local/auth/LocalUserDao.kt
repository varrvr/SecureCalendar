package com.example.calendar.data.local.auth

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalUserDao {

    @Query(
        """
        SELECT * FROM local_users
        ORDER BY username
        """
    )
    fun observeAll(): Flow<List<LocalUserEntity>>

    @Query(
        """
        SELECT * FROM local_users
        WHERE id = :userId
        LIMIT 1
        """
    )
    suspend fun getById(
        userId: Long
    ): LocalUserEntity?

    @Query(
        """
        SELECT * FROM local_users
        WHERE username = :username
        LIMIT 1
        """
    )
    suspend fun getByUsername(
        username: String
    ): LocalUserEntity?

    @Query("SELECT * FROM local_users WHERE username = :username COLLATE NOCASE LIMIT 1")
    suspend fun getByUsernameCaseInsensitive(username: String): LocalUserEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(
        user: LocalUserEntity
    ): Long

    @Update
    suspend fun update(
        user: LocalUserEntity
    )

    @Query(
        """
        UPDATE local_users
        SET serverRegistrationStatus = :status
        WHERE id = :userId
        """
    )
    suspend fun updateServerRegistrationStatus(
        userId: Long,
        status: String
    )
}
