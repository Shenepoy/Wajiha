package com.wajiha.data.db

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** v1 → v2: age classification column on games (community `rating` unchanged). */
val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE games ADD COLUMN ageRating TEXT")
        }
    }

/** v2 → v3: per-platform deep scan preference for ROM folder walks. */
val MIGRATION_2_3 =
    object : Migration(2, 3) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE platforms ADD COLUMN deepScan INTEGER NOT NULL DEFAULT 0",
            )
        }
    }
