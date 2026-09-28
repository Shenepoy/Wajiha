package com.wajiha.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlatformDao {
    @Query("SELECT * FROM platforms ORDER BY sortIndex, name")
    fun observeAll(): Flow<List<PlatformEntity>>

    @Query("SELECT * FROM platforms WHERE enabled = 1 ORDER BY sortIndex, name")
    fun observeEnabled(): Flow<List<PlatformEntity>>

    @Query("SELECT * FROM platforms WHERE id = :id")
    suspend fun byId(id: String): PlatformEntity?

    @Query("SELECT * FROM platforms WHERE id = :id")
    fun observeById(id: String): Flow<PlatformEntity?>

    @Query("SELECT * FROM platforms")
    suspend fun all(): List<PlatformEntity>

    @Upsert
    suspend fun upsert(platforms: List<PlatformEntity>)

    @Update
    suspend fun update(platform: PlatformEntity)

    @Query("UPDATE platforms SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(
        id: String,
        enabled: Boolean,
    )

    @Query("UPDATE platforms SET defaultEmulatorId = :emulatorId WHERE id = :id")
    suspend fun setDefaultEmulator(
        id: String,
        emulatorId: String?,
    )

    @Query("UPDATE platforms SET deepScan = :deepScan WHERE id = :id")
    suspend fun setDeepScan(
        id: String,
        deepScan: Boolean,
    )

    @Query("DELETE FROM platforms WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface EmulatorDao {
    @Query("SELECT * FROM emulators WHERE platformId = :platformId")
    suspend fun forPlatform(platformId: String): List<EmulatorEntity>

    @Query("SELECT * FROM emulators WHERE platformId = :platformId")
    fun observeForPlatform(platformId: String): Flow<List<EmulatorEntity>>

    @Query("SELECT * FROM emulators WHERE id = :id")
    suspend fun byId(id: String): EmulatorEntity?

    @Query("SELECT * FROM emulators")
    suspend fun all(): List<EmulatorEntity>

    @Query("SELECT * FROM emulators")
    fun observeAll(): Flow<List<EmulatorEntity>>

    @Upsert
    suspend fun upsert(emulators: List<EmulatorEntity>)

    @Query("DELETE FROM emulators WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT DISTINCT packageNames FROM emulators")
    suspend fun allPackageLists(): List<String>
}

@Dao
interface GameDao {
    @Query("SELECT * FROM games WHERE hidden = 0 ORDER BY sortName")
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE platformId = :platformId AND hidden = 0 ORDER BY sortName")
    fun observeForPlatform(platformId: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE favorite = 1 AND hidden = 0 ORDER BY sortName")
    fun observeFavorites(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE hidden = 0 AND lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<GameEntity>>

    @Query(
        "SELECT * FROM games WHERE hidden = 0 AND (displayName LIKE '%' || :query || '%' OR fileName LIKE '%' || :query || '%') ORDER BY sortName LIMIT 200",
    )
    fun search(query: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun byId(id: Long): GameEntity?

    @Query("SELECT * FROM games WHERE id = :id")
    fun observeById(id: Long): Flow<GameEntity?>

    @Query("SELECT * FROM games WHERE uri = :uri")
    suspend fun byUri(uri: String): GameEntity?

    @Query("SELECT uri FROM games WHERE platformId = :platformId")
    suspend fun urisForPlatform(platformId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(games: List<GameEntity>): List<Long>

    @Update
    suspend fun update(game: GameEntity)

    @Query("UPDATE games SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(
        id: Long,
        favorite: Boolean,
    )

    @Query("UPDATE games SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(
        id: Long,
        hidden: Boolean,
    )

    @Query("UPDATE games SET emulatorOverrideId = :emulatorId WHERE id = :id")
    suspend fun setEmulatorOverride(
        id: Long,
        emulatorId: String?,
    )

    @Query("UPDATE games SET crc32 = :crc32, md5 = :md5 WHERE id = :id")
    suspend fun setHashes(
        id: Long,
        crc32: String?,
        md5: String?,
    )

    @Query("UPDATE games SET playCount = playCount + 1, lastPlayedAt = :playedAt WHERE id = :id")
    suspend fun recordPlay(
        id: Long,
        playedAt: Long,
    )

    @Query("DELETE FROM games WHERE uri IN (:uris)")
    suspend fun deleteByUris(uris: List<String>)

    @Query("DELETE FROM games WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE games SET launchOnDisplay = :displayId WHERE id = :id")
    suspend fun setLaunchOnDisplay(
        id: Long,
        displayId: Int?,
    )

    @Query("SELECT COUNT(*) FROM games WHERE platformId = :platformId AND hidden = 0")
    fun observeCountForPlatform(platformId: String): Flow<Int>

    @Query("SELECT * FROM games WHERE crc32 = :crc32 LIMIT 1")
    suspend fun byCrc32(crc32: String): GameEntity?

    @Query("SELECT * FROM games WHERE crc32 IS NULL AND fileSize <= :maxBytes LIMIT :limit")
    suspend fun missingHashes(
        maxBytes: Long,
        limit: Int,
    ): List<GameEntity>

    @Query("SELECT * FROM games WHERE fileName = :fileName AND platformId = :platformId AND hidden = 0 LIMIT 2")
    suspend fun byFileNameAndPlatform(
        fileName: String,
        platformId: String,
    ): List<GameEntity>

    @Query("SELECT * FROM games WHERE fileName = :fileName AND hidden = 0")
    suspend fun byFileName(fileName: String): List<GameEntity>

    @Query(
        "SELECT * FROM games WHERE platformId = :platformId AND hidden = 0 AND " +
            "(fileName LIKE '%' || :serial || '%' OR displayName LIKE '%' || :serial || '%') LIMIT 2",
    )
    suspend fun bySerialHint(
        serial: String,
        platformId: String,
    ): List<GameEntity>

    @Query(
        "SELECT * FROM games WHERE hidden = 0 AND " +
            "(fileName LIKE '%' || :serial || '%' OR displayName LIKE '%' || :serial || '%') LIMIT 2",
    )
    suspend fun bySerialHintAnyPlatform(serial: String): List<GameEntity>
}

@Dao
interface RomFolderDao {
    @Query("SELECT * FROM rom_folders")
    fun observeAll(): Flow<List<RomFolderEntity>>

    @Query("SELECT * FROM rom_folders WHERE enabled = 1")
    suspend fun allEnabled(): List<RomFolderEntity>

    @Query("SELECT * FROM rom_folders WHERE platformId = :platformId")
    suspend fun forPlatform(platformId: String): List<RomFolderEntity>

    @Query("SELECT * FROM rom_folders WHERE id = :id")
    suspend fun byId(id: Long): RomFolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: RomFolderEntity): Long

    @Query("UPDATE rom_folders SET lastScanAt = :at WHERE id = :id")
    suspend fun markScanned(
        id: Long,
        at: Long,
    )

    @Query("UPDATE rom_folders SET scanDepth = :depth WHERE platformId = :platformId")
    suspend fun setScanDepthForPlatform(
        platformId: String,
        depth: Int,
    )

    @Query("DELETE FROM rom_folders WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface GameMediaDao {
    @Query("SELECT * FROM game_media WHERE gameId = :gameId")
    fun observeForGame(gameId: Long): Flow<List<GameMediaEntity>>

    @Query("SELECT * FROM game_media WHERE gameId = :gameId")
    suspend fun forGame(gameId: Long): List<GameMediaEntity>

    @Query("SELECT * FROM game_media WHERE gameId IN (:gameIds)")
    suspend fun forGames(gameIds: List<Long>): List<GameMediaEntity>

    @Query("SELECT * FROM game_media WHERE gameId = :gameId AND type = :type LIMIT 1")
    suspend fun forGameAndType(
        gameId: Long,
        type: String,
    ): GameMediaEntity?

    @Query("SELECT * FROM game_media WHERE type = :type")
    fun observeAllOfType(type: String): Flow<List<GameMediaEntity>>

    @Query(
        """
        SELECT game_media.* FROM game_media
        INNER JOIN games ON games.id = game_media.gameId
        WHERE games.platformId = :platformId AND game_media.type = 'boxart'
        LIMIT :limit
        """,
    )
    fun observeBoxartSample(
        platformId: String,
        limit: Int,
    ): Flow<List<GameMediaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(media: GameMediaEntity): Long

    @Query("DELETE FROM game_media WHERE gameId = :gameId AND type = :type")
    suspend fun deleteForGameAndType(
        gameId: Long,
        type: String,
    )

    @Query("DELETE FROM game_media WHERE gameId = :gameId")
    suspend fun deleteForGame(gameId: Long)

    @Query("DELETE FROM game_media WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PlaySessionDao {
    @Insert
    suspend fun insert(session: PlaySessionEntity): Long

    @Query("UPDATE play_sessions SET endedAt = :endedAt, durationSec = :durationSec WHERE id = :id")
    suspend fun close(
        id: Long,
        endedAt: Long,
        durationSec: Long,
    )

    @Query("SELECT * FROM play_sessions WHERE gameId = :gameId ORDER BY startedAt DESC")
    fun observeForGame(gameId: Long): Flow<List<PlaySessionEntity>>

    @Query("SELECT COALESCE(SUM(durationSec), 0) FROM play_sessions WHERE gameId = :gameId")
    fun observeTotalPlaytime(gameId: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(durationSec), 0) FROM play_sessions WHERE gameId = :gameId")
    suspend fun totalPlaytime(gameId: Long): Long

    @Query("SELECT * FROM play_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun latestOpen(): PlaySessionEntity?

    @Query(
        "SELECT * FROM play_sessions WHERE endedAt IS NULL AND packageName = :packageName " +
            "ORDER BY startedAt DESC LIMIT 1",
    )
    suspend fun latestOpenForPackage(packageName: String): PlaySessionEntity?

    @Query("SELECT * FROM play_sessions WHERE endedAt IS NULL ORDER BY startedAt DESC")
    suspend fun allOpen(): List<PlaySessionEntity>

    @Query("UPDATE play_sessions SET gameId = :gameId WHERE id = :id")
    suspend fun updateGameId(
        id: Long,
        gameId: Long?,
    )
}

data class CollectionCountRow(
    val collectionId: Long,
    val gameCount: Long,
)

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY sortIndex, name")
    fun observeAll(): Flow<List<CollectionEntity>>

    @Query("SELECT collectionId, COUNT(gameId) AS gameCount FROM collection_games GROUP BY collectionId")
    fun observeCounts(): Flow<List<CollectionCountRow>>

    @Query("SELECT collectionId FROM collection_games WHERE gameId = :gameId")
    fun observeCollectionIds(gameId: Long): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(collection: CollectionEntity): Long

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM collection_games WHERE collectionId = :collectionId")
    suspend fun removeAllGames(collectionId: Long)

    @Query(
        "SELECT games.* FROM games INNER JOIN collection_games ON games.id = collection_games.gameId " +
            "WHERE collection_games.collectionId = :collectionId AND games.hidden = 0 ORDER BY collection_games.position",
    )
    fun observeGames(collectionId: Long): Flow<List<GameEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addGame(ref: CollectionGameCrossRef)

    @Query("DELETE FROM collection_games WHERE collectionId = :collectionId AND gameId = :gameId")
    suspend fun removeGame(
        collectionId: Long,
        gameId: Long,
    )
}
