package com.example.calendar.data.local.crypto

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface Bee2KeyDao {
    @Query("SELECT * FROM bee2_keys WHERE ownerId = :ownerId LIMIT 1")
    suspend fun get(ownerId: Long): Bee2KeyEntity?

    @Query("SELECT * FROM bee2_keys WHERE ownerId = :ownerId LIMIT 1")
    fun observe(ownerId: Long): Flow<Bee2KeyEntity?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(key: Bee2KeyEntity)

    @Query("DELETE FROM bee2_keys WHERE ownerId = :ownerId")
    suspend fun delete(ownerId: Long)
}
