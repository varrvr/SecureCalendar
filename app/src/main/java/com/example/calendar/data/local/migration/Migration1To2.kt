package com.example.calendar.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {

    override fun migrate(db: SupportSQLiteDatabase) {

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS local_users (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                username TEXT NOT NULL,
                displayName TEXT NOT NULL,
                serverRegistrationStatus TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_local_users_username
            ON local_users(username)
            """.trimIndent()
        )

        db.execSQL(
            """
            ALTER TABLE events
            ADD COLUMN ownerId INTEGER NOT NULL DEFAULT 0
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_events_ownerId
            ON events(ownerId)
            """.trimIndent()
        )
    }
}