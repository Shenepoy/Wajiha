package com.wajiha.android.detect.probes

import com.wajiha.android.detect.EmulatorDataReader
import com.wajiha.android.detect.RomPathCandidate
import com.wajiha.android.detect.RomPathProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads RetroArch [content_history.lpl] — first playlist entry is the most recent game.
 * Path is resolved from [retroarch.cfg] when present.
 */
class RetroArchRomPathProbe(
    private val reader: EmulatorDataReader
) : RomPathProbe {

    override val probeId = "retroarch"
    override val supportedPackages = RETROARCH_PACKAGES
    override val priority = 20

    override suspend fun probe(packageName: String, sessionStartedAt: Long): RomPathCandidate? =
        withContext(Dispatchers.IO) {
            val historyPath = resolveHistoryPath(packageName) ?: return@withContext null
            val content = reader.readText(historyPath) ?: return@withContext null
            parseHistoryContent(content, sessionStartedAt, probeId)
        }

    internal fun resolveHistoryPath(packageName: String): String? {
        val cfgPath = "${reader.dataRootForPackage(packageName)}/retroarch.cfg"
        val cfg = runCatching {
            java.io.File(cfgPath).takeIf { it.isFile }?.readText()
        }.getOrNull()
        val fromCfg = cfg?.let { parseCfgValue(it, "content_history_path") }
        if (!fromCfg.isNullOrBlank() && reader.exists(fromCfg)) {
            return fromCfg
        }
        val playlistDir = cfg?.let { parseCfgValue(it, "playlist_directory") }
        if (!playlistDir.isNullOrBlank()) {
            val builtin = "$playlistDir/builtin/content_history.lpl"
            if (reader.exists(builtin)) return builtin
        }
        val defaults = listOf(
            "${reader.externalRoot()}/RetroArch/playlists/builtin/content_history.lpl",
            "${reader.dataRootForPackage(packageName)}/content_history.lpl"
        )
        return defaults.firstOrNull { reader.exists(it) }
    }

    internal fun parseHistoryEntry(content: String, sessionStartedAt: Long): RomPathCandidate? =
        parseHistoryContent(content, sessionStartedAt, probeId)

    companion object {
        val RETROARCH_PACKAGES = setOf(
            "com.retroarch",
            "com.retroarch.aarch64",
            "com.retroarch.ra32",
            "com.retroarch.plus"
        )

        private const val SESSION_SKEW_MS = 60_000L
        private val json = Json { ignoreUnknownKeys = true }

        fun parseHistoryContent(
            content: String,
            sessionStartedAt: Long,
            probeId: String = "retroarch"
        ): RomPathCandidate? {
            val trimmed = content.trim()
            if (trimmed.isEmpty()) return null
            return if (trimmed.startsWith("{")) {
                parseJsonHistoryStatic(trimmed, sessionStartedAt, probeId)
            } else {
                parseLegacyHistoryStatic(trimmed, sessionStartedAt, probeId)
            }
        }

        private fun parseJsonHistoryStatic(
            content: String,
            sessionStartedAt: Long,
            probeId: String
        ): RomPathCandidate? {
            val root = runCatching { json.parseToJsonElement(content).jsonObject }.getOrNull()
                ?: return null
            val items = root["items"]?.jsonArray ?: return null
            if (items.isEmpty()) return null

            val cutoff = sessionStartedAt - SESSION_SKEW_MS
            val picked = items.mapNotNull { element ->
                val obj = element.jsonObject
                val path = obj.stringValue("path") ?: return@mapNotNull null
                val ts = obj.longValue("entry_timestamp") ?: obj.longValue("last_played")
                Triple(path, obj.stringValue("core_name"), ts)
            }.filter { (_, _, ts) -> ts == null || ts >= cutoff }
                .maxByOrNull { it.third ?: Long.MIN_VALUE }
                ?: run {
                    val first = items.first().jsonObject
                    Triple(
                        first.stringValue("path") ?: return null,
                        first.stringValue("core_name"),
                        first.longValue("entry_timestamp")
                    )
                }

            val (path, coreName, ts) = picked
            return RomPathCandidate(
                rawPath = path,
                probeId = probeId,
                platformHint = coreName?.let(::platformFromCore),
                timestamp = ts
            )
        }

        private fun parseLegacyHistoryStatic(
            content: String,
            sessionStartedAt: Long,
            probeId: String
        ): RomPathCandidate? {
            val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size < 2) return null
            return RomPathCandidate(
                rawPath = lines[1],
                probeId = probeId,
                platformHint = null,
                timestamp = sessionStartedAt
            )
        }

        fun parseCfgValue(cfg: String, key: String): String? {
            val pattern = Regex("""^\s*$key\s*=\s*"?([^"\n]+)"?\s*$""", RegexOption.MULTILINE)
            val match = pattern.find(cfg) ?: return null
            return match.groupValues[1].trim().trim('"')
        }

        fun platformFromCore(coreName: String): String? {
            val normalized = coreName.lowercase()
            return CORE_HINTS.entries.firstOrNull { (prefix, _) ->
                normalized.contains(prefix)
            }?.value
        }

        private val CORE_HINTS = linkedMapOf(
            "snes9x" to "snes",
            "fceumm" to "nes",
            "nestopia" to "nes",
            "genesis_plus_gx" to "md",
            "picodrive" to "md",
            "pcsx_rearmed" to "psx",
            "beetle_psx" to "psx",
            "melonds" to "nds",
            "desmume" to "nds",
            "mupen64plus" to "n64",
            "parallel_n64" to "n64",
            "gambatte" to "gb",
            "vba_next" to "gba",
            "mgba" to "gba",
            "ppsspp" to "psp",
            "dolphin" to "gc",
            "flycast" to "dc",
            "beetle_pce" to "pce",
            "mednafen_pce" to "pce"
        )
    }
}

private fun JsonObject.stringValue(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.longValue(key: String): Long? =
    this[key]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
