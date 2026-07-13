package com.wajiha.data.scraper

import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaLogKind
import com.wajiha.log.logClockMs
import com.wajiha.log.truncateLogMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One scraper/RA HTTP exchange kept for the in-app log viewer. */
data class ScrapeApiLogEntry(
    val atMs: Long,
    val method: String,
    /** Full URL with secret query values replaced by `***`. */
    val url: String,
    val status: Int?,
    val ok: Boolean,
    val elapsedMs: Long?,
    val bytes: Int?,
    val detail: String?,
) {
    fun displayLine(): String {
        val statusPart = status?.toString() ?: "—"
        val timePart = elapsedMs?.let { "${it}ms" } ?: ""
        val sizePart = bytes?.let { "${it}B" } ?: ""
        val meta =
            listOf(statusPart, timePart, sizePart)
                .filter { it.isNotEmpty() }
                .joinToString(" ")
        val tail = detail?.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
        return "$method $url  [$meta]${if (ok) "" else " FAIL"}$tail"
    }
}

/**
 * Bounded in-memory scrape API log (no disk). Cap keeps memory tiny even during
 * long FillGaps runs; newest entries win.
 */
object ScrapeApiLog {
    private const val MAX_ENTRIES = 500

    private val _entries = MutableStateFlow<List<ScrapeApiLogEntry>>(emptyList())
    val entries: StateFlow<List<ScrapeApiLogEntry>> = _entries.asStateFlow()

    fun clear() {
        _entries.value = emptyList()
    }

    fun record(
        method: String = "GET",
        url: String,
        status: Int?,
        ok: Boolean,
        elapsedMs: Long? = null,
        bytes: Int? = null,
        detail: String? = null,
    ) {
        val entry =
            ScrapeApiLogEntry(
                atMs = logClockMs(),
                method = method,
                url = formatApiUrlForLog(url),
                status = status,
                ok = ok,
                elapsedMs = elapsedMs,
                bytes = bytes,
                detail = detail?.let { truncateLogMessage(it, maxLen = 200) },
            )
        _entries.update { prev ->
            val next = prev + entry
            if (next.size <= MAX_ENTRIES) next else next.takeLast(MAX_ENTRIES)
        }

        val line = entry.displayLine()
        if (ok) {
            WajihaLog.i(WajihaLogKind.NETWORK, line, minIntervalMs = 0L)
        } else {
            WajihaLog.w(WajihaLogKind.NETWORK, line)
        }
    }
}

private val secretQueryKeys =
    setOf(
        "password",
        "sspassword",
        "devpassword",
        "y",
        "apikey",
        "api_key",
        "key",
        "token",
        "access_token",
        "secret",
        "authorization",
        "auth",
    )

/**
 * Keeps path + query so logs show the real API call (systemeid, crc, game id, …)
 * while redacting credential parameters.
 */
fun formatApiUrlForLog(url: String): String {
    val qIndex = url.indexOf('?')
    if (qIndex < 0) return truncateLogMessage(url, maxLen = 560)
    val base = url.substring(0, qIndex)
    val query = url.substring(qIndex + 1)
    if (query.isBlank()) return truncateLogMessage(base, maxLen = 560)
    val redacted =
        query
            .split('&')
            .filter { it.isNotBlank() }
            .joinToString("&") { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) return@joinToString part
                val key = part.substring(0, eq)
                val value = part.substring(eq + 1)
                if (key.lowercase() in secretQueryKeys) {
                    "$key=***"
                } else {
                    val decodedLen = value.length
                    if (decodedLen > 120) "$key=${value.take(117)}…" else "$key=$value"
                }
            }
    return truncateLogMessage("$base?$redacted", maxLen = 560)
}
