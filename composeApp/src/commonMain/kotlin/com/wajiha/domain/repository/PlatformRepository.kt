package com.wajiha.domain.repository

import com.wajiha.data.db.EmulatorDao
import com.wajiha.data.db.EmulatorEntity
import com.wajiha.data.db.PlatformDao
import com.wajiha.data.db.PlatformEntity
import kotlinx.coroutines.flow.Flow

class PlatformRepository(
    private val platformDao: PlatformDao,
    private val emulatorDao: EmulatorDao,
) {
    fun observeAll(): Flow<List<PlatformEntity>> = platformDao.observeAll()

    fun observeEnabled(): Flow<List<PlatformEntity>> = platformDao.observeEnabled()

    fun observeById(id: String): Flow<PlatformEntity?> = platformDao.observeById(id)

    suspend fun byId(id: String): PlatformEntity? = platformDao.byId(id)

    suspend fun all(): List<PlatformEntity> = platformDao.all()

    suspend fun upsert(platforms: List<PlatformEntity>) = platformDao.upsert(platforms)

    suspend fun update(platform: PlatformEntity) = platformDao.update(platform)

    suspend fun setEnabled(
        id: String,
        enabled: Boolean,
    ) = platformDao.setEnabled(id, enabled)

    suspend fun setDefaultEmulator(
        id: String,
        emulatorId: String?,
    ) = platformDao.setDefaultEmulator(id, emulatorId)

    fun observeEmulators(platformId: String): Flow<List<EmulatorEntity>> = emulatorDao.observeForPlatform(platformId)

    suspend fun emulatorsFor(platformId: String): List<EmulatorEntity> = emulatorDao.forPlatform(platformId)

    suspend fun emulatorById(id: String): EmulatorEntity? = emulatorDao.byId(id)

    suspend fun allEmulators(): List<EmulatorEntity> = emulatorDao.all()

    suspend fun upsertEmulators(emulators: List<EmulatorEntity>) = emulatorDao.upsert(emulators)

    /** All known emulator package names, for foreground-app matching. */
    suspend fun knownEmulatorPackages(): Set<String> =
        emulatorDao
            .allPackageLists()
            .flatMap { it.split(',') }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

    /**
     * Resolve the emulator to use for a game: per-game override →
     * platform default → first emulator configured for the platform.
     */
    suspend fun resolveEmulator(
        platformId: String,
        overrideId: String?,
    ): EmulatorEntity? {
        overrideId?.let { emulatorDao.byId(it)?.let { e -> return e } }
        val platform = platformDao.byId(platformId)
        platform?.defaultEmulatorId?.let { emulatorDao.byId(it)?.let { e -> return e } }
        val candidates = emulatorDao.forPlatform(platformId)
        return candidates.firstOrNull { it.isDefault } ?: candidates.firstOrNull()
    }
}
