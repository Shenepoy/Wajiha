package com.wajiha.android.detect

/**
 * Emulator-specific reader that extracts a ROM path or filename hint from app data.
 */
interface RomPathProbe {
    val probeId: String
    val supportedPackages: Set<String>
    /** Lower values are tried first when multiple probes match. */
    val priority: Int

    suspend fun probe(packageName: String, sessionStartedAt: Long): RomPathCandidate?
}

data class RomPathCandidate(
    /** Filesystem or content URI path, when known. */
    val rawPath: String? = null,
    /** PS2/PS1 serial or basename when no full path is available. */
    val fileNameHint: String? = null,
    val probeId: String,
    val platformHint: String? = null,
    val timestamp: Long? = null
)

data class ResolvedGame(
    val gameId: Long?,
    val displayName: String,
    val platformId: String?,
    val boxartPath: String? = null,
    val heroPath: String? = null,
    val confidence: MatchConfidence,
    val source: String
)

enum class MatchConfidence {
    EXACT_URI,
    NORMALIZED_PATH,
    FILENAME,
    SERIAL,
    FILENAME_FALLBACK
}
