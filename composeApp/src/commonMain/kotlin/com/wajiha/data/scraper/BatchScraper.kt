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
import kotlinx.serialization.json.Json

@Serializable
data class BatchScrapeProgress(
    val running: Boolean = false,
    val paused: Boolean = false,
    val total: Int = 0,
    val done: Int = 0,
    val matched: Int = 0,
    val failed: Int = 0,
    val currentGameName: String? = null,
    /** gameId -> error message for the final report */
    val errors: Map<Long, String> = emptyMap()
)

/**
 * Persists the last [BatchScrapeProgress] snapshot so progress survives
 * process death (the WorkManager job itself is rescheduled by the system).
 */
class BatchProgressStore(private val dataStore: DataStore<Preferences>) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun load(): BatchScrapeProgress? =
        dataStore.data.first()[KEY]?.let {
            try {
                json.decodeFromString<BatchScrapeProgress>(it)
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

    fun requestCancel() {
        userCancelled = true
    }

    /** Seeds the UI with the persisted snapshot (e.g. last run's summary). */
    suspend fun restoreIfIdle() {
        if (restored || _progress.value.running || _progress.value.done > 0) return
        restored = true
        progressStore.load()?.let { saved ->
            if (!_progress.value.running) {
                // A snapshot still marked running means we died mid-run
                _progress.value = saved.copy(running = false, paused = false, currentGameName = null)
            }
        }
    }

    private val pauseFlag = MutableStateFlow(false)

    fun pause() {
        pauseFlag.value = true
        _progress.value = _progress.value.copy(paused = true)
    }

    fun resume() {
        pauseFlag.value = false
        _progress.value = _progress.value.copy(paused = false)
    }

    /**
     * Scrapes [games] sequentially (network concurrency is handled inside the
     * engine's download semaphore). Suspends while paused. Throws
     * [CancellationException] through when the hosting job is cancelled.
     */
    suspend fun run(games: List<GameEntity>) {
        val settings = settingsRepository.current()
        val targets = if (settings.skipAlreadyScraped) {
            games.filter { it.scrapedAt == null }
        } else {
            games
        }

        // Resume after process death: the persisted snapshot is still marked
        // running and the already-scraped games have just been filtered out.
        val checkpoint = progressStore.load()
        val base = if (settings.skipAlreadyScraped && checkpoint?.running == true) {
            checkpoint.copy(paused = false, currentGameName = null)
        } else {
            BatchScrapeProgress()
        }
        _progress.value = base.copy(running = true, total = base.done + targets.size)
        progressStore.save(_progress.value)
        userCancelled = false
        var interrupted = false

        try {
            for (game in targets) {
                pauseFlag.first { !it }
                _progress.value = _progress.value.copy(currentGameName = game.displayName)
                val result = try {
                    engine.scrapeGame(game, settings)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    GameScrapeResult(game.id, matched = false, error = e.message ?: "error")
                }
                _progress.value = _progress.value.let { p ->
                    p.copy(
                        done = p.done + 1,
                        matched = p.matched + if (result.matched) 1 else 0,
                        failed = p.failed + if (!result.matched) 1 else 0,
                        errors = if (result.error != null) p.errors + (game.id to result.error) else p.errors
                    )
                }
                progressStore.save(_progress.value)
            }
        } catch (e: CancellationException) {
            interrupted = true
            throw e
        } finally {
            _progress.value = _progress.value.copy(running = false, paused = false, currentGameName = null)
            pauseFlag.value = false
            // Checkpoint semantics: running=true means "interrupted, resume on
            // the next worker run". Kept for system stops (constraint loss,
            // process shutdown mid-cancel), cleared for user cancels.
            val resumable = interrupted && !userCancelled
            withContext(NonCancellable) {
                try {
                    progressStore.save(_progress.value.copy(running = resumable))
                } catch (_: Exception) {
                }
            }
        }
    }

    suspend fun runForPlatform(platformId: String?) {
        val all = gameRepository.observeAll().first()
        run(if (platformId == null) all else all.filter { it.platformId == platformId })
    }
}
