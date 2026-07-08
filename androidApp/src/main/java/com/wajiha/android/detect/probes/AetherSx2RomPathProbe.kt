package com.wajiha.android.detect.probes

import com.wajiha.android.detect.EmulatorDataReader
import com.wajiha.android.detect.RomPathCandidate
import com.wajiha.android.detect.RomPathProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Best-effort PS2 ROM hint for AetherSX2 / NetherSX2.
 *
 * Phase-0 spike on Thor found no accessible [settings.ini]; [playtime.dat] stores
 * per-serial timestamps and [sstates] filenames embed the active serial.
 */
class AetherSx2RomPathProbe(
    private val reader: EmulatorDataReader
) : RomPathProbe {

    override val probeId = "aethersx2"
    override val supportedPackages = setOf("xyz.aethersx2.android")
    override val priority = 10

    override suspend fun probe(packageName: String, sessionStartedAt: Long): RomPathCandidate? =
        withContext(Dispatchers.IO) {
            val root = reader.dataRootForPackage(packageName)
            probeSettingsIni(root)
                ?: probePlaytime("$root/playtime.dat", sessionStartedAt)
                ?: probeRecentSaveState("$root/sstates")
        }

    internal fun probeSettingsIni(root: String): RomPathCandidate? {
        val candidates = listOf(
            "$root/settings.ini",
            "$root/../settings.ini"
        )
        for (path in candidates) {
            val text = runCatching { File(path).takeIf { it.isFile }?.readText() }.getOrNull()
                ?: continue
            extractPathFromIni(text)?.let { romPath ->
                return RomPathCandidate(
                    rawPath = romPath,
                    probeId = probeId,
                    platformHint = PLATFORM_HINT
                )
            }
        }
        return null
    }

    internal fun probePlaytime(path: String, sessionStartedAt: Long): RomPathCandidate? {
        val text = runCatching { File(path).takeIf { it.isFile }?.readText() }.getOrNull()
            ?: return null
        val (serial, timestamp) = parsePlaytimeDat(text) ?: return null
        return RomPathCandidate(
            fileNameHint = serial,
            probeId = probeId,
            platformHint = PLATFORM_HINT,
            timestamp = timestamp
        )
    }

    internal fun probeRecentSaveState(dirPath: String): RomPathCandidate? {
        val newest = reader.newestFileInDir(dirPath, ".p2s") ?: return null
        val serial = serialFromSaveStateName(newest.name) ?: return null
        return RomPathCandidate(
            fileNameHint = serial,
            probeId = probeId,
            platformHint = PLATFORM_HINT,
            timestamp = newest.lastModified()
        )
    }

    internal fun parsePlaytimeDat(content: String): Pair<String, Long>? = Companion.parsePlaytimeDat(content)

    internal fun extractPathFromIni(ini: String): String? = Companion.extractPathFromIni(ini)

    internal fun serialFromSaveStateName(fileName: String): String? =
        Companion.serialFromSaveStateName(fileName)

    companion object {
        const val PLATFORM_HINT = "ps2"
        val SERIAL_REGEX = Regex("""[A-Z]{4}-\d{5}""")

        fun parsePlaytimeDat(content: String): Pair<String, Long>? =
            content.lineSequence()
                .mapNotNull { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) return@mapNotNull null
                    val parts = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
                    if (parts.size < 2) return@mapNotNull null
                    val serial = parts.first()
                    if (!SERIAL_REGEX.matches(serial)) return@mapNotNull null
                    val timestamp = parts.last().toLongOrNull() ?: return@mapNotNull null
                    serial to timestamp
                }
                .maxByOrNull { it.second }

        fun extractPathFromIni(ini: String): String? {
            val keys = listOf(
                "Filename",
                "DiscPath",
                "RecentISOFileName",
                "LastBootedFilename",
                "LastPlayedPath"
            )
            for (key in keys) {
                val pattern = Regex("""^\s*$key\s*=\s*(.+)\s*$""", RegexOption.MULTILINE)
                val match = pattern.find(ini) ?: continue
                val value = match.groupValues[1].trim().trim('"')
                if (value.isNotEmpty() && !value.equals("null", ignoreCase = true)) {
                    return value
                }
            }
            return null
        }

        fun serialFromSaveStateName(fileName: String): String? {
            val base = fileName.substringBeforeLast('.')
            val serial = base.substringBefore(' ').trim()
            return serial.takeIf { SERIAL_REGEX.matches(it) }
        }
    }
}
