package com.wajiha.android.detect

import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import com.wajiha.domain.repository.GameRepository
import com.wajiha.domain.scan.LibraryScanner
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Normalizes emulator-reported paths and matches them to library [GameEntity] rows.
 */
class RomPathMatcher(
    private val gameRepository: GameRepository,
) {
    suspend fun match(
        candidate: RomPathCandidate,
        platformHint: String? = candidate.platformHint,
    ): ResolvedGame? =
        withContext(Dispatchers.IO) {
            val rawPath = candidate.rawPath?.trim().orEmpty()
            val fileNameHint = candidate.fileNameHint?.trim().orEmpty()

            if (rawPath.startsWith("content://")) {
                gameRepository.byUri(rawPath)?.let { game ->
                    return@withContext resolvedFromGame(game, candidate.probeId, MatchConfidence.EXACT_URI)
                }
            }

            if (rawPath.isNotEmpty()) {
                val normalized = normalizePath(rawPath)
                val fileName = fileNameFromPath(normalized)
                if (fileName.isNotEmpty()) {
                    matchByFileName(fileName, platformHint, candidate.probeId)?.let { return@withContext it }
                }
                matchByNormalizedPathSuffix(normalized, platformHint, candidate.probeId)
                    ?.let { return@withContext it }
            }

            if (fileNameHint.isNotEmpty()) {
                if (looksLikeSerial(fileNameHint)) {
                    matchBySerial(fileNameHint, platformHint, candidate.probeId)
                        ?.let { return@withContext it }
                } else {
                    matchByFileName(fileNameHint, platformHint, candidate.probeId)
                        ?.let { return@withContext it }
                }
            }

            null
        }

    suspend fun filenameFallback(fileName: String): ResolvedGame? {
        val cleaned = LibraryScanner.cleanDisplayName(fileNameFromPath(fileName))
        if (cleaned.isBlank()) return null
        return ResolvedGame(
            gameId = null,
            displayName = cleaned,
            platformId = null,
            confidence = MatchConfidence.FILENAME_FALLBACK,
            source = "fallback",
        )
    }

    private suspend fun matchByFileName(
        fileName: String,
        platformHint: String?,
        source: String,
    ): ResolvedGame? {
        if (platformHint != null) {
            gameRepository.byFileNameAndPlatform(fileName, platformHint)?.let { game ->
                return resolvedFromGame(game, source, MatchConfidence.FILENAME)
            }
        }
        return gameRepository.byFileName(fileName)?.let { game ->
            resolvedFromGame(game, source, MatchConfidence.FILENAME)
        }
    }

    private suspend fun matchBySerial(
        serial: String,
        platformHint: String?,
        source: String,
    ): ResolvedGame? {
        val matches =
            if (platformHint != null) {
                gameRepository.bySerialHint(serial, platformHint)
            } else {
                gameRepository.bySerialHintAnyPlatform(serial)
            }
        if (matches.size != 1) {
            if (matches.size > 1) {
                WajihaLog.d(WajihaTags.EXTERNAL_RESOLVE, "ambiguous serial=$serial (${matches.size} hits)")
            }
            return null
        }
        return resolvedFromGame(matches.first(), source, MatchConfidence.SERIAL)
    }

    private suspend fun matchByNormalizedPathSuffix(
        normalizedPath: String,
        platformHint: String?,
        source: String,
    ): ResolvedGame? {
        val suffix = normalizedPath.lowercase()
        val uris =
            if (platformHint != null) {
                gameRepository.urisForPlatform(platformHint)
            } else {
                emptyList()
            }
        val hits =
            uris
                .mapNotNull { uri ->
                    val resolved = resolveSafUriToPath(Uri.parse(uri))?.lowercase() ?: return@mapNotNull null
                    if (resolved == suffix || resolved.endsWith("/$suffix") || suffix.endsWith(resolved)) {
                        gameRepository.byUri(uri)
                    } else {
                        null
                    }
                }.distinctBy { it.id }
        if (hits.size == 1) {
            return resolvedFromGame(hits.first(), source, MatchConfidence.NORMALIZED_PATH)
        }
        return null
    }

    private suspend fun resolvedFromGame(
        game: com.wajiha.data.db.GameEntity,
        source: String,
        confidence: MatchConfidence,
    ): ResolvedGame {
        val media = gameRepository.media(game.id)
        return ResolvedGame(
            gameId = game.id,
            displayName = game.displayName,
            platformId = game.platformId,
            boxartPath = media.firstOrNull { it.type == "boxart" }?.localPath,
            heroPath = media.firstOrNull { it.type == "hero" }?.localPath,
            logoPath = media.firstOrNull { it.type == "logo" }?.localPath,
            iconPath = media.firstOrNull { it.type == "icon" }?.localPath,
            squarePath = media.firstOrNull { it.type == "square" }?.localPath,
            confidence = confidence,
            source = source,
        )
    }

    companion object {
        fun normalizePath(raw: String): String {
            var path = raw.trim()
            if (path.startsWith("file://")) {
                path = path.removePrefix("file://")
            }
            path = path.replace('\\', '/')
            val sdcardRoot =
                runCatching {
                    Environment.getExternalStorageDirectory().absolutePath
                }.getOrDefault("/storage/emulated/0")
            if (path.startsWith("/sdcard/")) {
                path = sdcardRoot + path.removePrefix("/sdcard")
            }
            if (path.startsWith("sdcard/")) {
                path = "$sdcardRoot/${path.removePrefix("sdcard/")}"
            }
            while (path.contains("//")) {
                path = path.replace("//", "/")
            }
            return path
        }

        fun fileNameFromPath(path: String): String = path.substringAfterLast('/').substringAfterLast('\\')

        fun looksLikeSerial(value: String): Boolean = value.matches(Regex("""[A-Z]{4}-\d{5}"""))

        fun resolveSafUriToPath(uri: Uri): String? {
            try {
                if (uri.authority != "com.android.externalstorage.documents") return null
                var docId = DocumentsContract.getDocumentId(uri)
                if (docId.contains("%3A", ignoreCase = true)) {
                    docId = Uri.decode(docId)
                }
                val split = docId.split(":")
                if (split.size < 2) return null
                val (type, relPath) = split
                return if ("primary".equals(type, ignoreCase = true)) {
                    Environment.getExternalStorageDirectory().toString() + "/" + relPath
                } else {
                    "/storage/$type/$relPath"
                }
            } catch (_: Exception) {
                return null
            }
        }
    }
}
