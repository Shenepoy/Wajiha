package com.wajiha.data.ra

import kotlinx.serialization.Serializable

/**
 * Disk-backed RetroAchievements console library (compact md5 → game id plus
 * title list for fallback when the ROM uses a custom RA hash).
 */
interface RaHashLibraryStore {
    suspend fun load(consoleId: Int): RaHashLibraryDiskEntry?

    suspend fun save(
        consoleId: Int,
        entry: RaHashLibraryDiskEntry,
    )
}

@Serializable
data class RaListedGame(
    val id: Long,
    val title: String,
)

@Serializable
data class RaHashLibraryDiskEntry(
    /** Wall-clock epoch millis (not elapsedRealtime). */
    val fetchedAtMs: Long,
    val hashes: Map<String, Long> = emptyMap(),
    val games: List<RaListedGame> = emptyList(),
)

/** No persistence (tests / hosts without a files dir). */
object NoopRaHashLibraryStore : RaHashLibraryStore {
    override suspend fun load(consoleId: Int): RaHashLibraryDiskEntry? = null

    override suspend fun save(
        consoleId: Int,
        entry: RaHashLibraryDiskEntry,
    ) = Unit
}

/** In-memory console library used by [RaClient]. */
data class RaConsoleLibrary(
    val hashes: Map<String, Long>,
    val games: List<RaListedGame>,
    val fetchedAtMs: Long,
)

/**
 * Normalizes a ROM / RA title for fuzzy match: lowercase, drop extension and
 * common region/dump tags in parentheses or brackets.
 */
fun normalizeRaTitle(raw: String): String {
    var s = raw.trim().lowercase()
    val slash = maxOf(s.lastIndexOf('/'), s.lastIndexOf('\\'))
    if (slash >= 0 && slash < s.lastIndex) s = s.substring(slash + 1)
    val dot = s.lastIndexOf('.')
    if (dot > 0) s = s.substring(0, dot)
    s = PAREN_OR_BRACKET.replace(s, " ")
    s = NON_ALNUM.replace(s, " ")
    return s.split(WHITESPACE).filter { it.isNotBlank() }.joinToString(" ")
}

/**
 * Best RA game id for [displayName] against a console game list.
 * Prefers exact normalized match, then unique prefix/contains.
 */
fun matchRaGameIdByTitle(
    games: List<RaListedGame>,
    displayName: String,
): Long? {
    if (games.isEmpty()) return null
    val needle = normalizeRaTitle(displayName)
    if (needle.isBlank()) return null

    val exact = games.filter { normalizeRaTitle(it.title) == needle }
    if (exact.size == 1) return exact[0].id
    if (exact.size > 1) {
        // Prefer the shortest title (usually the base set, not hacks).
        return exact.minByOrNull { it.title.length }?.id
    }

    val prefixed =
        games.filter {
            val t = normalizeRaTitle(it.title)
            t.startsWith(needle) || needle.startsWith(t)
        }
    if (prefixed.size == 1) return prefixed[0].id

    val contained =
        games.filter {
            val t = normalizeRaTitle(it.title)
            t.contains(needle) || needle.contains(t)
        }
    if (contained.size == 1) return contained[0].id

    return null
}

private val PAREN_OR_BRACKET = Regex("""[\(\[\{][^\)\]\}]*[\)\]\}]""")
private val NON_ALNUM = Regex("""[^a-z0-9]+""")
private val WHITESPACE = Regex("""\s+""")
