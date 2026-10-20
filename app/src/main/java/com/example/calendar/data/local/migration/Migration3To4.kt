package com.example.calendar.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds device-bound, encrypted Bee2 key material without touching calendar rows. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS bee2_keys (
                ownerId INTEGER NOT NULL PRIMARY KEY,
                publicKey BLOB NOT NULL,
                encryptedPrivateKey BLOB NOT NULL,
                iv BLOB NOT NULL,
                fingerprint TEXT NOT NULL,
                FOREIGN KEY(ownerId) REFERENCES local_users(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )""".trimIndent()
        )
    }
}
