package com.wajiha.log

/**
 * Log categories. Production keeps a small always-on set at INFO/WARN;
 * verbose (debuggable builds) unlocks DEBUG for high-frequency kinds.
 *
 * Filter: `adb logcat -s Wajiha/Launch:I Wajiha/Input:D …`
 */
enum class WajihaLogKind(
    val tag: String,
) {
    /** Gamepad / key press & release */
    INPUT("Wajiha/Input"),

    /** Emulator / game startActivity */
    LAUNCH("Wajiha/Launch"),

    /** Activity lifecycle / window focus */
    WINDOW("Wajiha/Window"),

    /** Physical display attach/detach & dual-screen routing */
    DISPLAY("Wajiha/Display"),

    /** User preference changes */
    SETTINGS("Wajiha/Settings"),

    /** Scraper / RA / HTTP (never log secrets) */
    NETWORK("Wajiha/Network"),

    /** WorkManager, library scan, monitor background */
    WORK("Wajiha/Work"),

    /** Active game sessions / Now Playing */
    SESSION("Wajiha/Session"),

    /** Library scan details */
    LIBRARY("Wajiha/Library"),

    /** Adb debug dumps only */
    DEBUG("Wajiha/Debug"),
}

enum class WajihaLogLevel {
    DEBUG,
    INFO,
    WARN,
}

/**
 * Production vs verbose gate + light rate limits.
 *
 * - No file sink (logcat ring buffer only) — avoids storage growth.
 * - Rate limits drop floods (input/axis/poll) without allocating when disabled.
 * - [verbose] is set from Application when the APK is debuggable.
 */
object WajihaLogGate {
    @Volatile
    var verbose: Boolean = false

    /** Optional per-kind override; null = use default policy. */
    private val kindEnabled = HashMap<WajihaLogKind, Boolean>(WajihaLogKind.entries.size)

    /** Last emit time per kind for rate limiting (ms). */
    private val lastEmitMs = LongArray(WajihaLogKind.entries.size)

    fun setKindEnabled(
        kind: WajihaLogKind,
        enabled: Boolean?,
    ) {
        if (enabled == null) {
            kindEnabled.remove(kind)
        } else {
            kindEnabled[kind] = enabled
        }
    }

    fun isEnabled(
        kind: WajihaLogKind,
        level: WajihaLogLevel,
    ): Boolean {
        kindEnabled[kind]?.let { forced ->
            // Explicit on → all levels. Explicit off → keep WARN only (don't hide failures).
            return if (forced) true else level == WajihaLogLevel.WARN
        }
        return when (level) {
            WajihaLogLevel.WARN -> {
                true
            }

            WajihaLogLevel.INFO -> {
                verbose ||
                    kind == WajihaLogKind.LAUNCH ||
                    kind == WajihaLogKind.SESSION ||
                    kind == WajihaLogKind.WORK ||
                    kind == WajihaLogKind.SETTINGS ||
                    kind == WajihaLogKind.NETWORK ||
                    kind == WajihaLogKind.LIBRARY ||
                    kind == WajihaLogKind.WINDOW ||
                    kind == WajihaLogKind.DISPLAY ||
                    kind == WajihaLogKind.DEBUG
            }

            WajihaLogLevel.DEBUG -> {
                verbose
            }
        }
    }

    /**
     * @return true if this emit may proceed under [minIntervalMs] for [kind].
     * Call only after [isEnabled] returned true.
     */
    fun allowRate(
        kind: WajihaLogKind,
        minIntervalMs: Long,
        nowMs: Long = currentTimeMs(),
    ): Boolean {
        if (minIntervalMs <= 0L) return true
        val idx = kind.ordinal
        val last = lastEmitMs[idx]
        if (nowMs - last < minIntervalMs) return false
        lastEmitMs[idx] = nowMs
        return true
    }
}

internal expect fun currentTimeMs(): Long

/** Public clock for scraper timing from inline HTTP helpers. */
fun logClockMs(): Long = currentTimeMs()

internal expect fun platformLog(
    level: WajihaLogLevel,
    tag: String,
    message: String,
)

/** Cap message size so a single line cannot balloon logcat / memory. */
internal fun truncateLogMessage(
    message: String,
    maxLen: Int = MAX_LOG_MESSAGE_CHARS,
): String =
    if (message.length <= maxLen) {
        message
    } else {
        message.take(maxLen - 1) + "…"
    }

internal const val MAX_LOG_MESSAGE_CHARS = 640

/**
 * Cross-platform logging facade.
 *
 * Prefer kind-based APIs. Legacy string-tag overloads remain for existing call
 * sites and map to [WajihaLogKind] for gating.
 */
object WajihaLog {
    fun enabled(
        kind: WajihaLogKind,
        level: WajihaLogLevel,
    ): Boolean = WajihaLogGate.isEnabled(kind, level)

    fun d(
        kind: WajihaLogKind,
        message: String,
        minIntervalMs: Long = 0L,
    ) {
        emit(kind, WajihaLogLevel.DEBUG, message, minIntervalMs)
    }

    fun i(
        kind: WajihaLogKind,
        message: String,
        minIntervalMs: Long = 0L,
    ) {
        emit(kind, WajihaLogLevel.INFO, message, minIntervalMs)
    }

    fun w(
        kind: WajihaLogKind,
        message: String,
        minIntervalMs: Long = 0L,
    ) {
        emit(kind, WajihaLogLevel.WARN, message, minIntervalMs)
    }

    /** Setting change helper — always INFO/SETTINGS when gated on. */
    fun setting(
        key: String,
        value: Any?,
    ) {
        i(WajihaLogKind.SETTINGS, "setting: $key=$value")
    }

    // --- Legacy string-tag API (maps to kinds for production gating) ---

    fun d(
        tag: String,
        message: String,
    ) {
        emit(kindForTag(tag), WajihaLogLevel.DEBUG, message, minIntervalMs = 0L, tagOverride = tag)
    }

    fun i(
        tag: String,
        message: String,
    ) {
        emit(kindForTag(tag), WajihaLogLevel.INFO, message, minIntervalMs = 0L, tagOverride = tag)
    }

    fun w(
        tag: String,
        message: String,
    ) {
        emit(kindForTag(tag), WajihaLogLevel.WARN, message, minIntervalMs = 0L, tagOverride = tag)
    }

    private fun emit(
        kind: WajihaLogKind,
        level: WajihaLogLevel,
        message: String,
        minIntervalMs: Long,
        tagOverride: String? = null,
    ) {
        if (!WajihaLogGate.isEnabled(kind, level)) return
        if (!WajihaLogGate.allowRate(kind, minIntervalMs)) return
        platformLog(level, tagOverride ?: kind.tag, truncateLogMessage(message))
    }

    private fun kindForTag(tag: String): WajihaLogKind =
        when (tag) {
            WajihaTags.GAMEPAD,
            WajihaLogKind.INPUT.tag,
            "Wajiha/Gamepad",
            -> WajihaLogKind.INPUT

            WajihaTags.LAUNCH, WajihaLogKind.LAUNCH.tag -> WajihaLogKind.LAUNCH

            WajihaTags.WINDOW, WajihaLogKind.WINDOW.tag -> WajihaLogKind.WINDOW

            WajihaTags.DISPLAY, WajihaLogKind.DISPLAY.tag -> WajihaLogKind.DISPLAY

            WajihaTags.SETTINGS, WajihaLogKind.SETTINGS.tag -> WajihaLogKind.SETTINGS

            WajihaTags.SCRAPE,
            WajihaLogKind.NETWORK.tag,
            "Wajiha/Scrape",
            -> WajihaLogKind.NETWORK

            WajihaTags.WORK, WajihaLogKind.WORK.tag -> WajihaLogKind.WORK

            WajihaTags.NOW_PLAYING,
            WajihaLogKind.SESSION.tag,
            "Wajiha/NowPlaying",
            -> WajihaLogKind.SESSION

            WajihaTags.LIBRARY, WajihaLogKind.LIBRARY.tag -> WajihaLogKind.LIBRARY

            WajihaTags.DEBUG, WajihaLogKind.DEBUG.tag -> WajihaLogKind.DEBUG

            WajihaTags.EXTERNAL_RESOLVE -> WajihaLogKind.SESSION

            else -> WajihaLogKind.DEBUG
        }
}

/**
 * Adb filter tag strings. Prefer [WajihaLogKind] APIs.
 *
 * Renames (old → new): Gamepad→Input, NowPlaying→Session, Scrape→Network.
 * [kindForTag] still maps the old names if any leftover call sites pass them.
 */
object WajihaTags {
    const val DISPLAY = "Wajiha/Display"
    const val GAMEPAD = "Wajiha/Input"
    const val LAUNCH = "Wajiha/Launch"
    const val NOW_PLAYING = "Wajiha/Session"
    const val SCRAPE = "Wajiha/Network"
    const val LIBRARY = "Wajiha/Library"
    const val EXTERNAL_RESOLVE = "Wajiha/ExternalResolve"
    const val DEBUG = "Wajiha/Debug"
    const val WINDOW = "Wajiha/Window"
    const val SETTINGS = "Wajiha/Settings"
    const val WORK = "Wajiha/Work"
}

/** Strip query (credentials) and truncate path for short network tags. Prefer [formatApiUrlForLog]. */
fun sanitizeUrlForLog(url: String): String {
    // Kept for callers that want host+path only; secrets still stripped via query drop.
    val noQuery = url.substringBefore('?')
    return truncateLogMessage(noQuery, maxLen = 180)
}
