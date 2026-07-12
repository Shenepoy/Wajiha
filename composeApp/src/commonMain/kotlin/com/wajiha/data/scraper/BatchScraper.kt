package com.wajiha.data.scraper

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.wajiha.data.db.GameEntity
import com.wajiha.domain.repository.GameRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import com.wajiha.data.WajihaJson
import kotlin.concurrent.Volatile

private const val MAX_ISSUES = 100
private const val CHECKPOINT_INTERVAL_MS = 3_000L

@Serializable
data class BatchScrapeIssue(
    val gameId: Long,
    val gameName: String,
    val kind: ScrapeFailureKind,
    val message: String,
    val platformId: String? = null,
    /** matched | partial | no_match | error — for retry filtering */
    val outcome: GameScrapeOutcome = GameScrapeOutcome.Error
)

@Serializable
data class BatchScrapeProgress(
    val running: Boolean = false,
    val paused: Boolean = false,
    val total: Int = 0,
    val done: Int = 0,
    val matched: Int = 0,
    val partial: Int = 0,
    val noMatch: Int = 0,
    /** Hard errors (auth, network, exceptions, not configured). */
    val errorCount: Int = 0,
    val currentGameName: String? = null,
    val statusMessage: String? = null,
    val platformId: String? = null,
    val issues: List<BatchScrapeIssue> = emptyList(),
    /**
     * Legacy map kept for older checkpoints / notification helpers.
     * Prefer [issues].
     */
    val errors: Map<Long, String> = emptyMap(),
    /** @deprecated Use [errorCount] + [noMatch] + [partial]; kept for decode compat. */
    val failed: Int = 0
) {
    /** Games that did not fully succeed (partial + no match + errors). */
    val problemCount: Int get() = partial + noMatch + errorCount

    fun summaryLine(): String = buildString {
        append("$matched matched")
        if (partial > 0) append(", $partial partial")
        if (noMatch > 0) append(", $noMatch no match")
        if (errorCount > 0) append(", $errorCount errors")
        if (partial == 0 && noMatch == 0 && errorCount == 0 && failed > 0) {
            append(", $failed failed")
        }
    }
}

/**
 * Persists the last [BatchScrapeProgress] snapshot so progress survives
 * process death (the WorkManager job itself is rescheduled by the system).
 */
class BatchProgressStore(private val dataStore: DataStore<Preferences>) {

    private val json = WajihaJson.Default

    suspend fun load(): BatchScrapeProgress? =
        dataStore.data.first()[KEY]?.let {
            try {
                json.decodeFromString<BatchScrapeProgress>(it).normalized()
            } catch (_: Exception) {
                null
            }
        }

    suspend fun save(progress: BatchScrapeProgress) {
        dataStore.edit { prefs ->
            prefs[KEY] = json.encodeToString(BatchScrapeProgress.serializer(), progress)
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("scrape_batch_progress")
    }
}

/** Backfill counters from legacy [BatchScrapeProgress.failed] when needed. */
private fun BatchScrapeProgress.normalized(): BatchScrapeProgress {
    if (partial > 0 || noMatch > 0 || errorCount > 0 || issues.isNotEmpty()) {
        return this
    }
    if (failed <= 0) return this
    // Old checkpoints only had failed + errors map
    val fromErrors = errors.size
    return copy(
        errorCount = fromErrors,
        noMatch = (failed - fromErrors).coerceAtLeast(0),
        failed = failed
    )
}

/**
 * Drives a batch scrape over a set of games with progress, pause/resume and
 * cancel. Hosted by the Android foreground worker; observed by the UI.
 *
 * Progress is checkpointed to [BatchProgressStore] after every game. When the
 * process dies mid-run, WorkManager restarts the job: with skip-already-scraped
 * on, finished games are excluded and the counts carry on from the checkpoint.
 */
class BatchScraper(
    private val engine: ScrapeEngine,
    private val gameRepository: GameRepository,
    private val settingsRepository: ScraperSettingsRepository,
    private val progressStore: BatchProgressStore
) {
    private val _progress = MutableStateFlow(BatchScrapeProgress())
    val progress: StateFlow<BatchScrapeProgress> = _progress.asStateFlow()

    private var restored = false

    /** Set before cancelling the worker so the checkpoint isn't kept resumable. */
    @Volatile
    private var userCancelled = false

    /** When set, [run] scrapes only these game ids (retry path). */
    @Volatile
    private var retryGameIds: Set<Long>? = null

    fun requestCancel() {
        userCancelled = true
    }

    /** Seeds the UI with the persisted snapshot (e.g. last run's summary). */
    suspend fun restoreIfIdle() {
        if (restored || _progress.value.running || _progress.value.done > 0) return
        restored = true
        progressStore.load()?.let { saved ->
            if (!_progress.value.running) {
                _progress.value = saved.copy(
                    running = false,
                    paused = false,
                    currentGameName = null,
                    statusMessage = null
                )
            }
        }
    }

    private val pauseFlag = MutableStateFlow(false)

    fun pause() {
        pauseFlag.value = true
        _progress.value = _progress.value.copy(
            paused = true,
            statusMessage = "Paused"
        )
    }

    fun resume() {
        pauseFlag.value = false
        _progress.value = _progress.value.copy(
            paused = false,
            statusMessage = null
        )
    }

    /**
     * Preflight: returns an error message if batch should not start, else null.
     */
    suspend fun preflightMessage(platformId: String? = null): String? {
        val settings = settingsRepository.current()
        val effective = if (platformId != null) settings.forPlatform(platformId) else settings
        if (!engine.hasConfiguredSources(effective)) {
            return "No scraper sources configured — enable and sign in under Sources / Accounts"
        }
        if (_progress.value.running) {
            return "A scrape is already running"
        }
        return null
    }

    /**
     * Marks the next [run]/[runForPlatform] to only process Error + Partial
     * games from the last issues list (optionally filtered by [platformId]).
     */
    fun prepareRetryFailed(platformId: String? = null) {
        val issues = _progress.value.issues.filter { issue ->
            (issue.outcome == GameScrapeOutcome.Error ||
                issue.outcome == GameScrapeOutcome.Partial) &&
                (platformId == null || issue.platformId == platformId)
        }
        retryGameIds = issues.map { it.gameId }.toSet()
    }

    fun clearRetryFilter() {
        retryGameIds = null
    }

    fun hasRetryableIssues(platformId: String? = null): Boolean {
        val p = _progress.value
        if (p.running) return false
        return p.issues.any { issue ->
            (issue.outcome == GameScrapeOutcome.Error ||
                issue.outcome == GameScrapeOutcome.Partial) &&
                (platformId == null || issue.platformId == platformId)
        }
    }

    /**
     * Scrapes [games] sequentially (network concurrency is handled inside the
     * engine's download semaphore). Suspends while paused. Throws
     * [CancellationException] through when the hosting job is cancelled.
     */
    suspend fun run(
        games: List<GameEntity>,
        platformId: String? = null,
        policy: ScrapeRunPolicy = ScrapeRunPolicy.FillGaps
    ) {
        val settings = settingsRepository.current()
        val retryIds = retryGameIds
        retryGameIds = null

        val filtered = when {
            retryIds != null -> games.filter { it.id in retryIds }
            policy.mode == ScrapeRunMode.Force -> games
            policy.mode == ScrapeRunMode.FillGaps -> {
                val mediaByGame = gameRepository.mediaForGames(games.map { it.id })
                games.filter { game ->
                    game.needsGapFill(mediaByGame[game.id].orEmpty())
                }
            }
            else -> games
        }

        if (filtered.isEmpty()) {
            _progress.value = BatchScrapeProgress(
                running = false,
                platformId = platformId,
                statusMessage = if (retryIds != null) "Nothing to retry" else "Nothing to scrape"
            )
            progressStore.save(_progress.value)
            return
        }

        val checkpoint = progressStore.load()
        val canResume = retryIds == null &&
            policy.mode == ScrapeRunMode.FillGaps &&
            checkpoint?.running == true
        val base = if (canResume) {
            checkpoint.copy(paused = false, currentGameName = null, statusMessage = null)
        } else {
            BatchScrapeProgress(platformId = platformId)
        }
        _progress.value = base.copy(
            running = true,
            total = if (retryIds != null || !canResume) filtered.size else base.done + filtered.size,
            platformId = platformId ?: base.platformId,
            statusMessage = when {
                retryIds != null -> "Retrying failed…"
                policy.mode == ScrapeRunMode.Force -> "Force scraping…"
                else -> null
            },
            matched = if (retryIds != null || !canResume) 0 else base.matched,
            partial = if (retryIds != null || !canResume) 0 else base.partial,
            noMatch = if (retryIds != null || !canResume) 0 else base.noMatch,
            errorCount = if (retryIds != null || !canResume) 0 else base.errorCount,
            done = if (retryIds != null || !canResume) 0 else base.done,
            issues = if (retryIds != null || !canResume) emptyList() else base.issues,
            errors = if (retryIds != null || !canResume) emptyMap() else base.errors,
            failed = if (retryIds != null || !canResume) 0 else base.failed
        )
        progressStore.save(_progress.value)
        userCancelled = false
        var interrupted = false
        var lastCheckpointAt = 0L

        try {
            for (game in filtered) {
                pauseFlag.first { !it }
                _progress.value = _progress.value.copy(
                    currentGameName = game.displayName,
                    statusMessage = if (pauseFlag.value) "Paused" else null
                )
                val result = try {
                    engine.scrapeGame(game, settings, policy)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    GameScrapeResult(
                        gameId = game.id,
                        outcome = GameScrapeOutcome.Error,
                        failureKind = ScrapeFailureKind.Unknown,
                        message = e.message ?: "error"
                    )
                }
                _progress.value = applyResult(_progress.value, game, result)
                val now = System.currentTimeMillis()
                if (now - lastCheckpointAt >= CHECKPOINT_INTERVAL_MS) {
                    lastCheckpointAt = now
                    progressStore.save(_progress.value)
                }
            }
        } catch (e: CancellationException) {
            interrupted = true
            throw e
        } finally {
            _progress.value = _progress.value.copy(
                running = false,
                paused = false,
                currentGameName = null,
                statusMessage = null
            )
            pauseFlag.value = false
            val resumable = interrupted && !userCancelled
            withContext(NonCancellable) {
                try {
                    progressStore.save(_progress.value.copy(running = resumable))
                } catch (_: Exception) {
                }
            }
        }
    }

    suspend fun runForPlatform(
        platformId: String?,
        policy: ScrapeRunPolicy = ScrapeRunPolicy.FillGaps
    ) {
        val all = gameRepository.observeAll().first()
        run(
            games = if (platformId == null) all else all.filter { it.platformId == platformId },
            platformId = platformId,
            policy = policy
        )
    }

    private fun applyResult(
        progress: BatchScrapeProgress,
        game: GameEntity,
        result: GameScrapeResult
    ): BatchScrapeProgress {
        var matched = progress.matched
        var partial = progress.partial
        var noMatch = progress.noMatch
        var errorCount = progress.errorCount
        var issues = progress.issues

        when (result.outcome) {
            GameScrapeOutcome.Matched -> matched++
            GameScrapeOutcome.Partial -> {
                partial++
                issues = appendIssue(
                    issues,
                    BatchScrapeIssue(
                        gameId = game.id,
                        gameName = game.displayName,
                        kind = result.failureKind ?: ScrapeFailureKind.Download,
                        message = result.message ?: "Partial — no media saved",
                        platformId = game.platformId,
                        outcome = GameScrapeOutcome.Partial
                    )
                )
            }
            GameScrapeOutcome.NoMatch -> {
                noMatch++
                issues = appendIssue(
                    issues,
                    BatchScrapeIssue(
                        gameId = game.id,
                        gameName = game.displayName,
                        kind = ScrapeFailureKind.NoMatch,
                        message = result.sourceSummaryLine() ?: "No match",
                        platformId = game.platformId,
                        outcome = GameScrapeOutcome.NoMatch
                    )
                )
            }
            GameScrapeOutcome.Error -> {
                errorCount++
                val msg = result.message ?: "Error"
                issues = appendIssue(
                    issues,
                    BatchScrapeIssue(
                        gameId = game.id,
                        gameName = game.displayName,
                        kind = result.failureKind ?: ScrapeFailureKind.Unknown,
                        message = result.sourceSummaryLine() ?: msg,
                        platformId = game.platformId,
                        outcome = GameScrapeOutcome.Error
                    )
                )
            }
        }

        return progress.copy(
            done = progress.done + 1,
            matched = matched,
            partial = partial,
            noMatch = noMatch,
            errorCount = errorCount,
            // Legacy mirror for old checkpoints / helpers — prefer issue counters.
            failed = partial + noMatch + errorCount,
            issues = issues
        )
    }

    private fun appendIssue(
        issues: List<BatchScrapeIssue>,
        issue: BatchScrapeIssue
    ): List<BatchScrapeIssue> {
        val next = issues.filterNot { it.gameId == issue.gameId } + issue
        return if (next.size <= MAX_ISSUES) next else next.takeLast(MAX_ISSUES)
    }
}
