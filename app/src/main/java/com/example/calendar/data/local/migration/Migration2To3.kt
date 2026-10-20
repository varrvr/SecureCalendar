package com.example.calendar.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Adds local password verifiers. Existing accounts have no verifier and must register again. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE local_users ADD COLUMN passwordKdf TEXT NOT NULL DEFAULT 'PBKDF2WithHmacSHA256'")
        db.execSQL("ALTER TABLE local_users ADD COLUMN passwordSalt BLOB NOT NULL DEFAULT X''")
        db.execSQL("ALTER TABLE local_users ADD COLUMN passwordVerifier BLOB NOT NULL DEFAULT X''")
        db.execSQL("ALTER TABLE local_users ADD COLUMN passwordIterations INTEGER NOT NULL DEFAULT 310000")
    }
}
