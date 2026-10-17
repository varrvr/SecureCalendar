package com.example.calendar.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Query("SELECT * FROM events ORDER BY startTime")
    fun observeAll(): Flow<List<EventEntity>>

    @Insert
    suspend fun insert(event: EventEntity): Long

    @Update
    suspend fun update(event: EventEntity)

    @Delete
    suspend fun delete(event: EventEntity)

    @Query("SELECT * FROM events WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): EventEntity?

    @Query(
        """
        SELECT * FROM events
        WHERE startTime < :end
        AND endTime > :start
        ORDER BY startTime
        """
    )
    suspend fun getForPeriod(
        start: Long,
        end: Long
    ): List<EventEntity>

    @Query(
        """
        SELECT * FROM events
        WHERE isPublic = 1
        AND startTime < :end
        AND endTime > :start
        ORDER BY startTime
        """
    )
    suspend fun getPublicForPeriod(
        start: Long,
        end: Long
    ): List<EventEntity>
}
