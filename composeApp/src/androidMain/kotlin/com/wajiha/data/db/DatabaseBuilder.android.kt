package com.wajiha.data.db

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase

fun databaseBuilder(context: Context): RoomDatabase.Builder<WajihaDatabase> {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath(WajihaDatabase.NAME)
    return Room.databaseBuilder<WajihaDatabase>(
        context = appContext,
        name = dbFile.absolutePath,
    )
}

fun createWajihaDatabase(context: Context): WajihaDatabase = buildWajihaDatabase(databaseBuilder(context))
