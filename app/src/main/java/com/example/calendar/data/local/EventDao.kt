package com.example.calendar.data.local

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {

    @Query(
        """
        SELECT * FROM events
        WHERE ownerId = :ownerId
        ORDER BY startTime
        """
    )
    fun observeAll(
        ownerId: Long
    ): Flow<List<EventEntity>>

    @Query("INSERT INTO events (title, startTime, endTime, isPublic, ownerId) VALUES (:title, :startTime, :endTime, :isPublic, :ownerId)")
    suspend fun insertOwned(title: String, startTime: Long, endTime: Long, isPublic: Boolean, ownerId: Long): Long

    @Query(
        """
        SELECT * FROM events
        WHERE id = :id
        AND ownerId = :ownerId
        LIMIT 1
        """
    )
    suspend fun getById(
        ownerId: Long,
        id: Long
    ): EventEntity?

    @Query(
        """
        SELECT * FROM events
        WHERE ownerId = :ownerId
        AND startTime < :end
        AND endTime > :start
        ORDER BY startTime
        """
    )
    suspend fun getForPeriod(
        ownerId: Long,
        start: Long,
        end: Long
    ): List<EventEntity>

    @Query(
        """
        SELECT * FROM events
        WHERE ownerId = :ownerId
        AND isPublic = 1
        AND startTime < :end
        AND endTime > :start
        ORDER BY startTime
        """
    )
    suspend fun getPublicForPeriod(
        ownerId: Long,
        start: Long,
        end: Long
    ): List<EventEntity>

    @Query(
        """
        UPDATE events
        SET title = :title,
            startTime = :startTime,
            endTime = :endTime,
            isPublic = :isPublic
        WHERE id = :id
        AND ownerId = :ownerId
        """
    )
    suspend fun updateOwned(
        ownerId: Long,
        id: Long,
        title: String,
        startTime: Long,
        endTime: Long,
        isPublic: Boolean
    ): Int

    @Query(
        """
        DELETE FROM events
        WHERE id = :id
        AND ownerId = :ownerId
        """
    )
    suspend fun deleteOwned(
        ownerId: Long,
        id: Long
    ): Int
}
