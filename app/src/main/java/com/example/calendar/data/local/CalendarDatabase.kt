package com.example.calendar.data.local

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.calendar.data.local.auth.LocalUserDao
import com.example.calendar.data.local.auth.LocalUserEntity
import com.example.calendar.data.local.crypto.Bee2KeyEntity
import com.example.calendar.data.local.crypto.Bee2KeyDao
import com.example.calendar.data.local.migration.MIGRATION_1_2
import com.example.calendar.data.local.migration.MIGRATION_2_3
import com.example.calendar.data.local.migration.MIGRATION_3_4

@Database(
    entities = [
        EventEntity::class,
        LocalUserEntity::class,
        Bee2KeyEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class CalendarDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao

    abstract fun localUserDao(): LocalUserDao

    abstract fun bee2KeyDao(): Bee2KeyDao

    companion object {

        @Volatile
        private var instance: CalendarDatabase? = null

        fun getInstance(application: Application): CalendarDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    application,
                    CalendarDatabase::class.java,
                    "secure_calendar.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .addMigrations(MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
