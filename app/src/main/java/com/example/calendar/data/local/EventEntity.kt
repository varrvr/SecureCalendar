package com.example.calendar.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "events",
    indices = [
        Index("startTime"),
        Index("endTime"),
        Index("ownerId")
    ]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val title: String,

    val startTime: Long,

    val endTime: Long,

    val isPublic: Boolean = false,

    val ownerId: Long
)