package com.example.calendar.data.local

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [EventEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CalendarDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao
    companion object {

        @Volatile
        private var instance: CalendarDatabase? = null

        fun getInstance(application: Application): CalendarDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    application,
                    CalendarDatabase::class.java,
                    "secure_calendar.db"
                ).build().also {
                    instance = it
                }
            }
        }
    }
}
