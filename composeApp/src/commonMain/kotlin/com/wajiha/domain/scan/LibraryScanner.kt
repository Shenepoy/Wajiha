package com.wajiha.domain.scan

import com.wajiha.data.db.GameEntity
import com.wajiha.data.db.RomFolderEntity
import com.wajiha.data.prefs.SettingsRepository
import com.wajiha.domain.repository.GameRepository
import com.wajiha.domain.repository.PlatformRepository
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import com.wajiha.platform.RomHasher
import com.wajiha.platform.RomScanner
import com.wajiha.platform.ScannedRom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

data class ScanProgress(
    val running: Boolean = false,
    val currentFolder: String? = null,
    val foldersDone: Int = 0,
    val foldersTotal: Int = 0,
    val gamesAdded: Int = 0,
    val gamesRemoved: Int = 0,
    val gamesSkipped: Int = 0,
    val error: String? = null
)

/**
 * Incremental library scan: walks each enabled ROM folder, matches files
 * against platform extensions, hides multi-disc track files when an entry
 * file (.cue/.gdi/.m3u) exists in the same directory, then diffs against the
 * database (insert new URIs, delete vanished ones per platform).
 */
class LibraryScanner(
    private val gameRepository: GameRepository,
    private val platformRepository: PlatformRepository,
    private val romScanner: RomScanner,
    private val romHasher: RomHasher,
    private val settingsRepository: SettingsRepository,
    private val now: () -> Long
) {
    private val _progress = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = _progress

    suspend fun scanAll(): ScanProgress {
        val folders = gameRepository.enabledRomFolders()
        return scanFolders(folders)
    }

    suspend fun scanPlatform(platformId: String): ScanProgress {
        val folders = gameRepository.romFoldersFor(platformId).filter { it.enabled }
        return scanFolders(folders)
    }

    private suspend fun scanFolders(folders: List<RomFolderEntity>): ScanProgress {
        _progress.value = ScanProgress(running = true, foldersTotal = folders.size)
        var added = 0
        var removed = 0
        var skipped = 0
        val appSettings = settingsRepository.settings.first()
        val ignoreEnabled = appSettings.ignorePatternFilesEnabled
        val ignorePatterns = appSettings.ignoreFileNamePatterns
        // platformId → all uris seen this run; null value marks a failed folder
        val seenByPlatform = mutableMapOf<String, MutableSet<String>?>()

        folders.forEachIndexed { index, folder ->
            _progress.value = _progress.value.copy(
                currentFolder = folder.treeUri,
                foldersDone = index
            )
            val platform = platformRepository.byId(folder.platformId) ?: return@forEachIndexed
            val extensions = buildSet {
                platform.extensions.split(',').forEach { ext ->
                    ext.trim().lowercase().takeIf { it.isNotEmpty() }?.let { add(it) }
                }
                folder.extraExtensions?.split(',')?.forEach { ext ->
                    ext.trim().lowercase().takeIf { it.isNotEmpty() }?.let { add(it) }
                }
            }
            val scanned = try {
                romScanner.scan(folder.treeUri, extensions, folder.scanDepth)
            } catch (e: Exception) {
                seenByPlatform[folder.platformId] = null
                _progress.value = _progress.value.copy(error = e.message)
                return@forEachIndexed
            }

            val afterMultiDisc = filterMultiDiscTracks(scanned)
            val visible = if (ignoreEnabled) {
                val filtered = filterIgnoredNamePatterns(afterMultiDisc, ignorePatterns)
                skipped += afterMultiDisc.size - filtered.size
                filtered
            } else {
                afterMultiDisc
            }
            val bucket = seenByPlatform.getOrPut(folder.platformId) { mutableSetOf() }
            bucket?.addAll(visible.map { it.uri })

            val existingUris = gameRepository.urisForPlatform(folder.platformId).toSet()
            val newRoms = visible.filter { it.uri !in existingUris }
            if (newRoms.isNotEmpty()) {
                val timestamp = now()
                val inserted = gameRepository.insertAll(
                    newRoms.map { rom -> rom.toGameEntity(folder.platformId, timestamp) }
                )
                added += inserted.count { it != -1L }
            }
            gameRepository.markFolderScanned(folder.id, now())
        }

        // Remove games whose files vanished — only when every folder of the
        // platform scanned successfully this run.
        for ((platformId, seen) in seenByPlatform) {
            if (seen == null) continue
            val allFolders = gameRepository.romFoldersFor(platformId).filter { it.enabled }
            val scannedAll = allFolders.all { f -> folders.any { it.id == f.id } }
            if (!scannedAll) continue
            val dbUris = gameRepository.urisForPlatform(platformId)
            val gone = dbUris.filterNot { it in seen }
            if (gone.isNotEmpty()) {
                gameRepository.deleteByUris(gone)
                removed += gone.size
            }
        }

        val result = _progress.value.copy(
            running = false,
            currentFolder = null,
            foldersDone = folders.size,
            gamesAdded = added,
            gamesRemoved = removed,
            gamesSkipped = skipped
        )
        if (skipped > 0) {
            WajihaLog.i(
                WajihaTags.LIBRARY,
                "scan: skipped $skipped ROM(s) matching ignore name patterns"
            )
        }
        _progress.value = result
        return result
    }

    /**
     * Lazy hashing pass: compute CRC32 (and MD5 for small files) for games
     * that don't have hashes yet. Called from a background worker.
     */
    suspend fun computeMissingHashes(limit: Int = 50): Int {
        val games = gameRepository.missingHashes(CRC_MAX_BYTES, limit)
        for (game in games) {
            val crc = romHasher.crc32(game.uri, CRC_MAX_BYTES)
            val md5 = if (game.fileSize in 1..MD5_MAX_BYTES) {
                romHasher.md5(game.uri, MD5_MAX_BYTES)
            } else null
            if (crc != null || md5 != null) {
                gameRepository.setHashes(game.id, crc, md5)
            }
        }
        return games.size
    }

    private fun ScannedRom.toGameEntity(platformId: String, timestamp: Long): GameEntity {
        val baseName = fileName.substringBeforeLast('.')
        return GameEntity(
            uri = uri,
            platformId = platformId,
            displayName = cleanDisplayName(baseName),
            sortName = baseName.lowercase(),
            fileName = fileName,
            fileSize = size,
            addedAt = timestamp
        )
    }

    companion object {
        const val CRC_MAX_BYTES = 512L * 1024 * 1024
        const val MD5_MAX_BYTES = 128L * 1024 * 1024

        /** Entry-file extensions that subsume sibling track files. */
        private val entryExtensions = setOf("cue", "gdi", "m3u")

        /** Track/data files hidden when an entry file shares their directory. */
        private val trackExtensions = setOf("bin", "img", "sub", "raw", "wav", "ccd")

        /** Disc files hidden when an .m3u playlist shares their directory. */
        private val discExtensions = setOf("cue", "gdi", "chd", "iso", "pbp")

        fun filterIgnoredNamePatterns(
            roms: List<ScannedRom>,
            patterns: List<String>
        ): List<ScannedRom> {
            val needles = patterns.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (needles.isEmpty()) return roms
            return roms.filter { rom ->
                val fileHaystack = rom.fileName.lowercase()
                val pathHaystack = rom.uri.lowercase()
                needles.none { needle -> needle in fileHaystack || needle in pathHaystack }
            }
        }

        fun filterMultiDiscTracks(roms: List<ScannedRom>): List<ScannedRom> {
            val byParent = roms.groupBy { it.parentId }
            return byParent.values.flatMap { group ->
                val hasEntry = group.any { it.extension() in entryExtensions }
                val hasM3u = group.any { it.extension() == "m3u" }
                group.filter { rom ->
                    val ext = rom.extension()
                    when {
                        hasM3u && ext in discExtensions -> false
                        hasEntry && ext in trackExtensions -> false
                        else -> true
                    }
                }
            }
        }

        private fun ScannedRom.extension() = fileName.substringAfterLast('.', "").lowercase()

        /** "Chrono Trigger (USA) [!]" → "Chrono Trigger" for display. */
        fun cleanDisplayName(rawName: String): String =
            rawName
                .replace(Regex("""\s*[\(\[][^\)\]]*[\)\]]"""), "")
                .replace(Regex("""\s+"""), " ")
                .trim()
                .ifEmpty { rawName }
    }
}
