package com.wajiha.data.ra

import com.wajiha.data.WajihaJson
import com.wajiha.data.scraper.ScrapeApiLog
import com.wajiha.data.scraper.formatApiUrlForLog
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaLogKind
import com.wajiha.log.WajihaTags
import com.wajiha.log.logClockMs
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Serializable
data class RaUserProfile(
    @SerialName("User") val user: String = "",
    @SerialName("ULID") val ulid: String = "",
    @SerialName("TotalPoints") val totalPoints: Int = 0,
    @SerialName("TotalTruePoints") val totalTruePoints: Int = 0,
    @SerialName("UserPic") val userPic: String? = null,
)

@Serializable
data class RaAchievement(
    @SerialName("ID") val id: Long = 0,
    @SerialName("Title") val title: String = "",
    @SerialName("Description") val description: String = "",
    @SerialName("Points") val points: Int = 0,
    @SerialName("BadgeName") val badgeName: String = "",
    @SerialName("DisplayOrder") val displayOrder: Int = 0,
    @SerialName("DateEarned") val dateEarned: String? = null,
    @SerialName("DateEarnedHardcore") val dateEarnedHardcore: String? = null,
) {
    val earned: Boolean get() = dateEarned != null || dateEarnedHardcore != null
    val badgeUrl: String get() = RaMediaUrls.badge(badgeName)
    val badgeLockedUrl: String get() = RaMediaUrls.badgeLocked(badgeName)
}

@Serializable
data class RaGameProgress(
    @SerialName("ID") val id: Long = 0,
    @SerialName("Title") val title: String = "",
    @SerialName("ConsoleName") val consoleName: String = "",
    @SerialName("ImageIcon") val imageIcon: String? = null,
    @SerialName("NumAchievements") val numAchievements: Int = 0,
    @SerialName("NumAwardedToUser") val numAwardedToUser: Int = 0,
    @SerialName("NumAwardedToUserHardcore") val numAwardedToUserHardcore: Int = 0,
    @SerialName("UserCompletion") val userCompletion: String? = null,
    @SerialName("Achievements") val achievements: Map<String, RaAchievement> = emptyMap(),
) {
    val sortedAchievements: List<RaAchievement>
        get() = achievements.values.sortedBy { it.displayOrder }
}

/** Basic game metadata from [API_GetGame.php] (read-only). */
@Serializable
data class RaGameSummary(
    @SerialName("ID") val id: Long = 0,
    @SerialName("Title") val title: String? = null,
    @SerialName("GameTitle") val gameTitle: String? = null,
    @SerialName("ConsoleID") val consoleId: Int? = null,
    @SerialName("Genre") val genre: String? = null,
    @SerialName("Developer") val developer: String? = null,
    @SerialName("Publisher") val publisher: String? = null,
    @SerialName("Released") val released: String? = null,
    @SerialName("ImageIcon") val imageIcon: String? = null,
    @SerialName("GameIcon") val gameIcon: String? = null,
    @SerialName("ImageTitle") val imageTitle: String? = null,
    @SerialName("ImageBoxArt") val imageBoxArt: String? = null,
) {
    val displayTitle: String? get() = title?.takeIf { it.isNotBlank() } ?: gameTitle
    val iconPath: String? get() = imageIcon?.takeIf { it.isNotBlank() } ?: gameIcon
}

@Serializable
private data class RaGameListEntry(
    @SerialName("ID") val id: Long = 0,
    @SerialName("Title") val title: String = "",
    @SerialName("Hashes") val hashes: List<String> = emptyList(),
)

/**
 * Thin read-only client for the RetroAchievements Web API.
 *
 * Hash → game id uses [API_GetGameList.php] with `h=1` (there is no public
 * `API_GetGameInfoByHash` endpoint — that 404s). Auth: `y` (web API key);
 * `z` kept for official-client parity. Real HTTP is paced; GetGameList is
 * disk-cached aggressively per RA usage guidelines.
 */
class RaClient(
    private val http: HttpClient,
    private val hashLibraryStore: RaHashLibraryStore = NoopRaHashLibraryStore,
) {
    /** consoleId → session memory library (hashes + titles). */
    private val libraryCache = mutableMapOf<Int, RaConsoleLibrary>()

    private val summaryCache = mutableMapOf<Long, CachedSummary>()
    private val consoleFetchLocks = mutableMapOf<Int, Mutex>()
    private val consoleLocksGuard = Mutex()
    private val httpMutex = Mutex()
    private var lastHttpAtMs = 0L
    private var coolUntilMs = 0L

    /** Validates credentials by fetching the user's profile; null on failure. */
    suspend fun profile(
        username: String,
        apiKey: String,
    ): RaUserProfile? {
        val started = logClockMs()
        val url = "$BASE/API_GetUserProfile.php"
        return try {
            val response =
                pacedGet(url) {
                    parameter("z", username)
                    parameter("y", apiKey)
                    parameter("u", username)
                } ?: return null
            val body = response.body<String>()
            val status = response.status.value
            if (status in 500..599 || status == 429) {
                tripCooldown("profile http=$status")
            }
            val profile =
                WajihaJson.Lenient
                    .decodeFromString<RaUserProfile>(body)
                    .takeIf { it.user.isNotBlank() || it.ulid.isNotBlank() }
            ScrapeApiLog.record(
                url = response.request.url.toString(),
                status = status,
                ok = profile != null,
                elapsedMs = logClockMs() - started,
                bytes = body.length,
                detail =
                    if (profile != null) {
                        "ra profile user=${profile.user} ulid=${profile.ulid.isNotBlank()}"
                    } else {
                        "ra profile empty"
                    },
            )
            profile
        } catch (e: Exception) {
            ScrapeApiLog.record(
                url = formatApiUrlForLog("$url?u=$username"),
                status = null,
                ok = false,
                elapsedMs = logClockMs() - started,
                detail = "ra profile fail=${e.message}",
            )
            null
        }
    }

    /**
     * Resolves a ROM to an RA game id: hash library first, then title match.
     * PS2/PSX/etc. use custom RA hashes (not raw file MD5), so title fallback
     * is required for CHD/ISO dumps until rcheevos hashing lands.
     */
    suspend fun resolveGameId(
        username: String,
        apiKey: String,
        md5: String?,
        displayName: String?,
        consoleId: Int,
    ): ResolveRaGameId {
        val library =
            consoleLibrary(username, apiKey, consoleId)
                ?: return ResolveRaGameId.Miss(reason = "no console library")
        val hashKey = md5?.lowercase()?.takeIf { it.isNotBlank() }
        if (hashKey != null) {
            library.hashes[hashKey]?.let { id ->
                WajihaLog.i(
                    WajihaLogKind.NETWORK,
                    "ra resolve console=$consoleId via=hash md5=${hashKey.take(8)}… → $id",
                )
                return ResolveRaGameId.Hit(id, via = "hash")
            }
        }
        val name = displayName?.takeIf { it.isNotBlank() }
        if (name != null) {
            matchRaGameIdByTitle(library.games, name)?.let { id ->
                WajihaLog.i(
                    WajihaLogKind.NETWORK,
                    "ra resolve console=$consoleId via=title name=${name.take(48)} → $id " +
                        "(hashMiss=${hashKey != null})",
                )
                return ResolveRaGameId.Hit(id, via = "title")
            }
        }
        WajihaLog.i(
            WajihaLogKind.NETWORK,
            "ra resolve miss console=$consoleId md5=${hashKey?.take(8) ?: "-"} " +
                "name=${name?.take(48) ?: "-"} libGames=${library.games.size} " +
                "libHashes=${library.hashes.size}",
        )
        return ResolveRaGameId.Miss(
            reason =
                if (hashKey != null) {
                    "hash+title miss (custom RA hash systems need title or RA hash)"
                } else {
                    "title miss / no md5"
                },
        )
    }

    /** @deprecated Prefer [resolveGameId]; kept for call sites that only have a hash. */
    suspend fun gameIdForHash(
        username: String,
        apiKey: String,
        md5: String,
        consoleId: Int,
    ): Long? =
        when (
            val r =
                resolveGameId(
                    username = username,
                    apiKey = apiKey,
                    md5 = md5,
                    displayName = null,
                    consoleId = consoleId,
                )
        ) {
            is ResolveRaGameId.Hit -> r.raGameId
            is ResolveRaGameId.Miss -> null
        }

    /** Cached console library (GetGameList hashes + titles). */
    suspend fun consoleLibrary(
        username: String,
        apiKey: String,
        consoleId: Int,
    ): RaConsoleLibrary? {
        libraryCache[consoleId]?.let { return it }

        val lock = consoleLock(consoleId)
        return lock.withLock {
            libraryCache[consoleId]?.let { return@withLock it }

            val now = wallClockMs()
            val disk = hashLibraryStore.load(consoleId)
            val diskFresh =
                disk != null &&
                    disk.hashes.isNotEmpty() &&
                    disk.games.isNotEmpty() &&
                    (now - disk.fetchedAtMs) in 0 until HASH_LIBRARY_TTL_MS
            if (diskFresh && disk != null) {
                val lib =
                    RaConsoleLibrary(
                        hashes = disk.hashes,
                        games = disk.games,
                        fetchedAtMs = disk.fetchedAtMs,
                    )
                libraryCache[consoleId] = lib
                ScrapeApiLog.record(
                    url = formatApiUrlForLog("$BASE/API_GetGameList.php?i=$consoleId&h=1"),
                    status = 200,
                    ok = true,
                    elapsedMs = 0,
                    bytes = 0,
                    detail =
                        "ra game list disk-hit console=$consoleId " +
                            "hashes=${disk.hashes.size} games=${disk.games.size} " +
                            "ageMs=${now - disk.fetchedAtMs}",
                )
                return@withLock lib
            }

            val fetched = fetchConsoleLibrary(username, apiKey, consoleId)
            if (fetched != null) {
                libraryCache[consoleId] = fetched
                hashLibraryStore.save(
                    consoleId,
                    RaHashLibraryDiskEntry(
                        fetchedAtMs = fetched.fetchedAtMs,
                        hashes = fetched.hashes,
                        games = fetched.games,
                    ),
                )
                return@withLock fetched
            }

            // Soft-fallback: stale disk (even title-less) beats nothing.
            if (disk != null && disk.hashes.isNotEmpty()) {
                val lib =
                    RaConsoleLibrary(
                        hashes = disk.hashes,
                        games = disk.games,
                        fetchedAtMs = disk.fetchedAtMs,
                    )
                libraryCache[consoleId] = lib
                WajihaLog.w(
                    WajihaTags.SCRAPE,
                    "ra game list soft-fallback console=$consoleId " +
                        "hashes=${disk.hashes.size} games=${disk.games.size}",
                )
                return@withLock lib
            }
            null
        }
    }

    /** Basic game metadata ([API_GetGame.php]). Read-only; ~24h memory TTL. */
    suspend fun gameSummary(
        username: String,
        apiKey: String,
        raGameId: Long,
    ): RaGameSummary? {
        val now = logClockMs()
        summaryCache[raGameId]?.let { cached ->
            if (now - cached.fetchedAtMs < SUMMARY_TTL_MS) return cached.summary
        }
        val started = logClockMs()
        val url = "$BASE/API_GetGame.php"
        return try {
            val response =
                pacedGet(url) {
                    parameter("z", username)
                    parameter("y", apiKey)
                    parameter("i", raGameId)
                } ?: return null
            val status = response.status.value
            val body = response.body<String>()
            if (status == 429 || status in 500..599) {
                tripCooldown("game summary http=$status")
                ScrapeApiLog.record(
                    url = response.request.url.toString(),
                    status = status,
                    ok = false,
                    elapsedMs = logClockMs() - started,
                    bytes = body.length,
                    detail = "ra game summary id=$raGameId http=$status",
                )
                return summaryCache[raGameId]?.summary
            }
            if (status !in 200..299) {
                ScrapeApiLog.record(
                    url = response.request.url.toString(),
                    status = status,
                    ok = false,
                    elapsedMs = logClockMs() - started,
                    bytes = body.length,
                    detail = "ra game summary id=$raGameId http=$status",
                )
                return null
            }
            val summary =
                WajihaJson.Lenient
                    .decodeFromString<RaGameSummary>(body)
                    .takeIf { it.id > 0 || !it.displayTitle.isNullOrBlank() }
                    ?.let { if (it.id > 0) it else it.copy(id = raGameId) }
            if (summary != null) {
                summaryCache[raGameId] = CachedSummary(summary, logClockMs())
            }
            ScrapeApiLog.record(
                url = response.request.url.toString(),
                status = status,
                ok = summary != null,
                elapsedMs = logClockMs() - started,
                bytes = body.length,
                detail = "ra game summary id=$raGameId",
            )
            summary
        } catch (e: Exception) {
            ScrapeApiLog.record(
                url = formatApiUrlForLog("$url?i=$raGameId"),
                status = null,
                ok = false,
                elapsedMs = logClockMs() - started,
                detail = "ra game summary fail=${e.message}",
            )
            summaryCache[raGameId]?.summary
        }
    }

    /**
     * Full achievement list with the user's earned state.
     * [userId] should be ULID when known (stable); username is still accepted.
     */
    suspend fun gameProgress(
        username: String,
        apiKey: String,
        userId: String,
        raGameId: Long,
    ): RaGameProgress? {
        val started = logClockMs()
        val url = "$BASE/API_GetGameInfoAndUserProgress.php"
        val target = userId.ifBlank { username }
        return try {
            val response =
                pacedGet(url) {
                    parameter("z", username)
                    parameter("y", apiKey)
                    parameter("g", raGameId)
                    parameter("u", target)
                } ?: return null
            val status = response.status.value
            val body = response.body<String>()
            if (status == 429 || status in 500..599) {
                tripCooldown("progress http=$status")
            }
            val progress =
                WajihaJson.Lenient.decodeFromString<RaGameProgress>(body).takeIf { it.id > 0 }
            ScrapeApiLog.record(
                url = response.request.url.toString(),
                status = status,
                ok = progress != null,
                elapsedMs = logClockMs() - started,
                bytes = body.length,
                detail = "ra progress gameId=$raGameId achievements=${progress?.numAchievements}",
            )
            progress
        } catch (e: Exception) {
            ScrapeApiLog.record(
                url = formatApiUrlForLog("$url?g=$raGameId"),
                status = null,
                ok = false,
                elapsedMs = logClockMs() - started,
                detail = "ra progress fail=${e.message}",
            )
            null
        }
    }

    private suspend fun fetchConsoleLibrary(
        username: String,
        apiKey: String,
        consoleId: Int,
    ): RaConsoleLibrary? {
        val started = logClockMs()
        val url = "$BASE/API_GetGameList.php"
        return try {
            val response =
                pacedGet(url) {
                    parameter("z", username)
                    parameter("y", apiKey)
                    parameter("i", consoleId)
                    parameter("f", 1)
                    parameter("h", 1)
                } ?: return null
            val status = response.status.value
            val body = response.body<String>()
            if (status == 429 || status in 500..599) {
                tripCooldown("game list http=$status")
                ScrapeApiLog.record(
                    url = response.request.url.toString(),
                    status = status,
                    ok = false,
                    elapsedMs = logClockMs() - started,
                    bytes = body.length,
                    detail = "ra game list console=$consoleId http=$status",
                )
                return null
            }
            if (status !in 200..299) {
                ScrapeApiLog.record(
                    url = response.request.url.toString(),
                    status = status,
                    ok = false,
                    elapsedMs = logClockMs() - started,
                    bytes = body.length,
                    detail = "ra game list console=$consoleId http=$status",
                )
                return null
            }
            val entries =
                WajihaJson.Lenient.decodeFromString<List<RaGameListEntry>>(body)
            val map = HashMap<String, Long>(entries.sumOf { it.hashes.size }.coerceAtLeast(16))
            val games = ArrayList<RaListedGame>(entries.size)
            for (entry in entries) {
                if (entry.id <= 0) continue
                if (entry.title.isNotBlank()) {
                    games.add(RaListedGame(id = entry.id, title = entry.title))
                }
                for (hash in entry.hashes) {
                    val key = hash.lowercase()
                    if (key.isNotBlank()) map[key] = entry.id
                }
            }
            ScrapeApiLog.record(
                url = response.request.url.toString(),
                status = status,
                ok = true,
                elapsedMs = logClockMs() - started,
                bytes = body.length,
                detail =
                    "ra game list console=$consoleId games=${games.size} hashes=${map.size}",
            )
            RaConsoleLibrary(
                hashes = map,
                games = games,
                fetchedAtMs = wallClockMs(),
            )
        } catch (e: Exception) {
            ScrapeApiLog.record(
                url = formatApiUrlForLog("$url?i=$consoleId&h=1"),
                status = null,
                ok = false,
                elapsedMs = logClockMs() - started,
                detail = "ra game list fail=${e.message}",
            )
            null
        }
    }

    private suspend fun consoleLock(consoleId: Int): Mutex =
        consoleLocksGuard.withLock {
            consoleFetchLocks.getOrPut(consoleId) { Mutex() }
        }

    private suspend fun pacedGet(
        url: String,
        block: io.ktor.client.request.HttpRequestBuilder.() -> Unit,
    ): HttpResponse? =
        httpMutex.withLock {
            val now = logClockMs()
            if (now < coolUntilMs) {
                WajihaLog.w(
                    WajihaTags.SCRAPE,
                    "ra http skipped — cooldown ${coolUntilMs - now}ms left",
                )
                return@withLock null
            }
            val wait = lastHttpAtMs + MIN_HTTP_SPACING_MS - now
            if (wait > 0) delay(wait)
            lastHttpAtMs = logClockMs()
            http.get(url, block)
        }

    private fun tripCooldown(reason: String) {
        coolUntilMs = logClockMs() + COOLDOWN_MS
        WajihaLog.w(WajihaTags.SCRAPE, "ra circuit open ${COOLDOWN_MS}ms — $reason")
    }

    @OptIn(ExperimentalTime::class)
    private fun wallClockMs(): Long = Clock.System.now().toEpochMilliseconds()

    private data class CachedSummary(
        val summary: RaGameSummary,
        val fetchedAtMs: Long,
    )

    private companion object {
        const val BASE = "https://retroachievements.org/API"

        /** Our policy — RA docs say GetGameList data rarely changes. */
        const val HASH_LIBRARY_TTL_MS = 7L * 24 * 60 * 60 * 1000

        /** Our policy — game metadata is static. */
        const val SUMMARY_TTL_MS = 24L * 60 * 60 * 1000

        /** Our guardrail — docs publish no fixed rate. */
        const val MIN_HTTP_SPACING_MS = 400L
        const val COOLDOWN_MS = 60_000L
    }
}

sealed class ResolveRaGameId {
    data class Hit(
        val raGameId: Long,
        val via: String,
    ) : ResolveRaGameId()

    data class Miss(
        val reason: String,
    ) : ResolveRaGameId()
}
