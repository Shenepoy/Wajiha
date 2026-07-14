package com.wajiha.domain.repository

import com.wajiha.data.db.GameDao
import com.wajiha.data.db.GameEntity
import com.wajiha.data.db.GameMediaDao
import com.wajiha.data.db.GameMediaEntity
import com.wajiha.data.db.RomFolderDao
import com.wajiha.data.db.RomFolderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GameRepository(
    private val gameDao: GameDao,
    private val mediaDao: GameMediaDao,
    private val romFolderDao: RomFolderDao,
) {
    fun observeAll(): Flow<List<GameEntity>> = gameDao.observeAll()

    fun observeForPlatform(platformId: String): Flow<List<GameEntity>> = gameDao.observeForPlatform(platformId)

    fun observeFavorites(): Flow<List<GameEntity>> = gameDao.observeFavorites()

    fun observeRecent(limit: Int = 20): Flow<List<GameEntity>> = gameDao.observeRecent(limit)

    fun search(query: String): Flow<List<GameEntity>> = gameDao.search(query)

    fun observeById(id: Long): Flow<GameEntity?> = gameDao.observeById(id)

    fun observeCountForPlatform(platformId: String): Flow<Int> = gameDao.observeCountForPlatform(platformId)

    suspend fun byId(id: Long): GameEntity? = gameDao.byId(id)

    suspend fun byUri(uri: String): GameEntity? = gameDao.byUri(uri)

    suspend fun byCrc32(crc32: String): GameEntity? = gameDao.byCrc32(crc32)

    suspend fun byFileName(fileName: String): GameEntity? = gameDao.byFileName(fileName).singleOrNull()

    suspend fun byFileNameAndPlatform(
        fileName: String,
        platformId: String,
    ): GameEntity? = gameDao.byFileNameAndPlatform(fileName, platformId).singleOrNull()

    suspend fun bySerialHint(
        serial: String,
        platformId: String,
    ): List<GameEntity> = gameDao.bySerialHint(serial, platformId)

    suspend fun bySerialHintAnyPlatform(serial: String): List<GameEntity> = gameDao.bySerialHintAnyPlatform(serial)

    suspend fun missingHashes(
        maxBytes: Long,
        limit: Int,
    ): List<GameEntity> = gameDao.missingHashes(maxBytes, limit)

    suspend fun urisForPlatform(platformId: String): List<String> = gameDao.urisForPlatform(platformId)

    suspend fun insertAll(games: List<GameEntity>): List<Long> = gameDao.insertAll(games)

    suspend fun update(game: GameEntity) = gameDao.update(game)

    suspend fun deleteByUris(uris: List<String>) = gameDao.deleteByUris(uris)

    suspend fun deleteById(id: Long) = gameDao.deleteById(id)

    /** Removes DB entry and scraped media; keeps the ROM file on disk. */
    suspend fun removeFromLibrary(id: Long) {
        mediaDao.deleteForGame(id)
        gameDao.deleteById(id)
    }

    suspend fun setLaunchOnDisplay(
        id: Long,
        displayId: Int?,
    ) = gameDao.setLaunchOnDisplay(id, displayId)

    suspend fun setFavorite(
        id: Long,
        favorite: Boolean,
    ) = gameDao.setFavorite(id, favorite)

    suspend fun setHidden(
        id: Long,
        hidden: Boolean,
    ) = gameDao.setHidden(id, hidden)

    suspend fun setEmulatorOverride(
        id: Long,
        emulatorId: String?,
    ) = gameDao.setEmulatorOverride(id, emulatorId)

    suspend fun setHashes(
        id: Long,
        crc32: String?,
        md5: String?,
    ) = gameDao.setHashes(id, crc32, md5)

    suspend fun recordPlay(
        id: Long,
        playedAt: Long,
    ) = gameDao.recordPlay(id, playedAt)

    // Media
    fun observeMedia(gameId: Long): Flow<List<GameMediaEntity>> = mediaDao.observeForGame(gameId)

    fun observeAllBoxart(): Flow<List<GameMediaEntity>> = mediaDao.observeAllOfType("boxart")

    /** Local boxart paths for a platform sample (settings hero / platform row). */
    fun observeBoxartSample(
        platformId: String,
        limit: Int = 8,
    ): Flow<List<String>> =
        mediaDao.observeBoxartSample(platformId, limit).map { list ->
            list.mapNotNull { it.localPath }
        }

    suspend fun boxartSample(
        platformId: String,
        limit: Int = 8,
    ): List<String> = mediaDao.boxartSample(platformId, limit).mapNotNull { it.localPath }

    fun observeAllVideos(): Flow<List<GameMediaEntity>> = mediaDao.observeAllOfType("video")

    fun observeAllHeroes(): Flow<List<GameMediaEntity>> = mediaDao.observeAllOfType("hero")

    fun observeAllLogos(): Flow<List<GameMediaEntity>> = mediaDao.observeAllOfType("logo")

    fun observeAllIcons(): Flow<List<GameMediaEntity>> = mediaDao.observeAllOfType("icon")

    suspend fun media(gameId: Long): List<GameMediaEntity> = mediaDao.forGame(gameId)

    suspend fun mediaForGames(gameIds: List<Long>): Map<Long, List<GameMediaEntity>> {
        if (gameIds.isEmpty()) return emptyMap()
        return gameIds
            .chunked(500)
            .flatMap { chunk ->
                mediaDao.forGames(chunk)
            }.groupBy { it.gameId }
    }

    suspend fun mediaOfType(
        gameId: Long,
        type: String,
    ): GameMediaEntity? = mediaDao.forGameAndType(gameId, type)

    suspend fun saveMedia(media: GameMediaEntity): Long {
        mediaDao.deleteForGameAndType(media.gameId, media.type)
        return mediaDao.insert(media)
    }

    suspend fun deleteMedia(id: Long) = mediaDao.delete(id)

    // ROM folders
    fun observeRomFolders(): Flow<List<RomFolderEntity>> = romFolderDao.observeAll()

    suspend fun enabledRomFolders(): List<RomFolderEntity> = romFolderDao.allEnabled()

    suspend fun romFoldersFor(platformId: String): List<RomFolderEntity> = romFolderDao.forPlatform(platformId)

    suspend fun addRomFolder(folder: RomFolderEntity): Long = romFolderDao.insert(folder)

    suspend fun removeRomFolder(id: Long) = romFolderDao.delete(id)

    suspend fun markFolderScanned(
        id: Long,
        at: Long,
    ) = romFolderDao.markScanned(id, at)
}
