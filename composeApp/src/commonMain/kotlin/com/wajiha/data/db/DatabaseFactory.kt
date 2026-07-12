package com.wajiha.data.db

import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

fun buildWajihaDatabase(builder: RoomDatabase.Builder<WajihaDatabase>): WajihaDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(MIGRATION_1_2)
        .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
        .build()
