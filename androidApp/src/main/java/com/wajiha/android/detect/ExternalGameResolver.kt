package com.wajiha.android.detect

import com.wajiha.data.prefs.SettingsRepository
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Orchestrates per-emulator ROM path probes and library matching for external sessions.
 */
class ExternalGameResolver(
    private val probes: List<RomPathProbe>,
    private val matcher: RomPathMatcher,
    private val settingsRepository: SettingsRepository,
    private val reader: EmulatorDataReader
) {
    private val mutex = Mutex()
    private val lastProbeAt = mutableMapOf<String, Long>()
    private val lastResolved = mutableMapOf<String, ResolvedGame>()

    suspend fun resolve(packageName: String, sessionStartedAt: Long): ResolvedGame? =
        withContext(Dispatchers.IO) {
            val settings = settingsRepository.settings.first()
            if (!settings.romReconciliationEnabled) {
                return@withContext null
            }
            if (!reader.canRead()) {
                WajihaLog.d(WajihaTags.EXTERNAL_RESOLVE, "skip $packageName: storage not readable")
                return@withContext null
            }

            mutex.withLock {
                val now = System.currentTimeMillis()
                val lastAt = lastProbeAt[packageName] ?: 0L
                if (now - lastAt < MIN_PROBE_INTERVAL_MS) {
                    return@withContext lastResolved[packageName]
                }
                lastProbeAt[packageName] = now

                val probe = probes
                    .filter { packageName in it.supportedPackages }
                    .minByOrNull { it.priority }
                    ?: run {
                        WajihaLog.d(WajihaTags.EXTERNAL_RESOLVE, "no probe for $packageName")
                        return@withContext null
                    }

                val candidate = runCatching {
                    probe.probe(packageName, sessionStartedAt)
                }.getOrNull()

                if (candidate == null) {
                    WajihaLog.d(WajihaTags.EXTERNAL_RESOLVE, "${probe.probeId}: no candidate for $packageName")
                    return@withContext null
                }

                val matched = matcher.match(candidate)
                val resolved = matched ?: run {
                    val hint = candidate.rawPath?.let { RomPathMatcher.fileNameFromPath(it) }
                        ?: candidate.fileNameHint
                    if (settings.romReconciliationShowFilenameFallback && !hint.isNullOrBlank()) {
                        matcher.filenameFallback(hint)
                    } else {
                        null
                    }
                }

                if (resolved != null) {
                    lastResolved[packageName] = resolved
                    WajihaLog.i(
                        WajihaTags.EXTERNAL_RESOLVE,
                        "${probe.probeId}: $packageName -> gameId=${resolved.gameId} " +
                            "(${resolved.confidence}, ${resolved.displayName})"
                    )
                }
                resolved
            }
        }

    fun clearSession(packageName: String) {
        lastProbeAt.remove(packageName)
        lastResolved.remove(packageName)
    }

    private companion object {
        const val MIN_PROBE_INTERVAL_MS = 10_000L
    }
}
