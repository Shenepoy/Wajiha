package com.wajiha.data.db

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor

@Database(
    entities = [
        PlatformEntity::class,
        EmulatorEntity::class,
        GameEntity::class,
        RomFolderEntity::class,
        GameMediaEntity::class,
        PlaySessionEntity::class,
        CollectionEntity::class,
        CollectionGameCrossRef::class
    ],
    version = 2,
    exportSchema = true
)
@ConstructedBy(WajihaDatabaseConstructor::class)
abstract class WajihaDatabase : RoomDatabase() {
    abstract fun platformDao(): PlatformDao
    abstract fun emulatorDao(): EmulatorDao
    abstract fun gameDao(): GameDao
    abstract fun romFolderDao(): RomFolderDao
    abstract fun gameMediaDao(): GameMediaDao
    abstract fun playSessionDao(): PlaySessionDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        const val NAME = "wajiha.db"
    }
}

@Suppress("KotlinNoActualForExpect")
expect object WajihaDatabaseConstructor : RoomDatabaseConstructor<WajihaDatabase> {
    override fun initialize(): WajihaDatabase
}
