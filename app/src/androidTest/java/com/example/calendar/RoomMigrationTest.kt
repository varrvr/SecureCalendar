package com.example.calendar

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.calendar.data.local.CalendarDatabase
import com.example.calendar.data.local.migration.MIGRATION_1_2
import com.example.calendar.data.local.migration.MIGRATION_2_3
import com.example.calendar.data.local.migration.MIGRATION_3_4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RoomMigrationTest {
    @Test fun versionOneEventsSurviveAllMigrations() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "migration-${System.nanoTime()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, startTime INTEGER NOT NULL, endTime INTEGER NOT NULL, isPublic INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX index_events_startTime ON events(startTime)")
            db.execSQL("CREATE INDEX index_events_endTime ON events(endTime)")
            db.execSQL("INSERT INTO events (id,title,startTime,endTime,isPublic) VALUES (7,'old meeting',100,200,0)")
            db.version = 1
        }
        val database = Room.databaseBuilder(context, CalendarDatabase::class.java, name)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
        try {
            val sql = database.openHelper.writableDatabase
            sql.query("SELECT title, ownerId FROM events WHERE id=7").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("old meeting", cursor.getString(0))
                assertEquals(0L, cursor.getLong(1))
            }
            assertEquals(4, sql.query("PRAGMA user_version").use { cursor -> cursor.moveToFirst(); cursor.getInt(0) })
            sql.query("PRAGMA table_info(bee2_keys)").use { cursor ->
                assertTrue(cursor.moveToFirst())
            }
        } finally {
            database.close()
            File(file.path + "-wal").delete()
            File(file.path + "-shm").delete()
            file.delete()
        }
    }
}
