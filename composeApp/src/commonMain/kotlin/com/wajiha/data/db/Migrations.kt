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
