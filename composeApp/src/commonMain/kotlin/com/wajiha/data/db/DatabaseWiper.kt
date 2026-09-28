package com.wajiha.data.db

/**
 * Clears Wajiha's own data and returns the process to first-run setup.
 * ROM trees and installed emulator packages stay.
 */
fun interface DatabaseWiper {
    suspend fun wipeAllTables()
}

/** Room table wipe, kept off the Android app classpath. */
fun interface StoredDataCleaner {
    suspend fun clear()
}

class RoomStoredDataCleaner(
    private val database: WajihaDatabase,
) : StoredDataCleaner {
    override suspend fun clear() {
        database.clearAllTables()
    }
}
