package com.wajiha.ui.scraper.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wajiha.data.db.GameEntity
import com.wajiha.data.scraper.MatchConfidence
import com.wajiha.data.scraper.MatchRankPolicy
import com.wajiha.data.scraper.MediaCandidate
import com.wajiha.data.scraper.MediaRanker
import com.wajiha.data.scraper.MediaType
import com.wajiha.data.scraper.RankedMediaOption
import com.wajiha.data.scraper.ScrapeCandidate
import com.wajiha.data.scraper.ScrapeEngine
import com.wajiha.data.scraper.ScrapeRunPolicy
import com.wajiha.data.scraper.ScraperSettings
import com.wajiha.data.scraper.ScraperSettingsRepository
import com.wajiha.data.scraper.StagedMediaPick
import com.wajiha.domain.repository.GameRepository
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Shared review picker / queue controller used by platform scraper, game detail,
 * and Settings → Scraper manual match.
 */
class ScrapeReviewViewModel(
    private val engine: ScrapeEngine,
    private val settingsRepository: ScraperSettingsRepository,
    private val gameRepository: GameRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ScrapeReviewState())
    val state: StateFlow<ScrapeReviewState> = _state.asStateFlow()

    private var queue: List<GameEntity> = emptyList()
    private var queueIndex: Int = 0
    private var onQueueFinished: (() -> Unit)? = null

    fun reset() {
        queue = emptyList()
        queueIndex = 0
        onQueueFinished = null
        _state.value = ScrapeReviewState()
    }

    /**
     * Single-game review. [lockedSlots] non-null = single-element Manual.
     * [openPickerImmediately] opens the first locked slot's picker (preview entry).
     */
    fun openGame(
        game: GameEntity,
        focusMediaType: MediaType? = null,
        preferredCandidate: ScrapeCandidate? = null,
        lockedSlots: Set<ReviewSlot>? = null,
        openPickerImmediately: Boolean = false,
    ) {
        val locked =
            lockedSlots
                ?: focusMediaType?.let { ReviewSlot.forMediaType(it)?.let { slot -> setOf(slot) } }
        queue = listOf(game)
        queueIndex = 0
        onQueueFinished = null
        loadCurrent(
            lockedSlots = locked,
            preferredCandidate = preferredCandidate,
            openPickerImmediately = openPickerImmediately || locked != null && focusMediaType != null,
        )
    }

    /** Platform review queue. */
    fun openQueue(
        games: List<GameEntity>,
        onFinished: (() -> Unit)? = null,
    ) {
        queue = games
        queueIndex = 0
        onQueueFinished = onFinished
        if (games.isEmpty()) {
            _state.value =
                ScrapeReviewState(
                    message = "Nothing to review",
                    messageSuccess = null,
                    queueTotal = 0,
                )
            onFinished?.invoke()
            return
        }
        loadCurrent()
    }

    fun setDraftSearchName(name: String) {
        _state.update { it.copy(draftSearchName = name) }
    }

    /**
     * Applies draft search. Refreshes active/locked slot only; invalidates other caches.
     */
    fun search(name: String? = null) {
        val query =
            name?.takeIf { it.isNotBlank() }
                ?: _state.value.draftSearchName.takeIf { it.isNotBlank() }
                ?: _state.value.searchName
        val active =
            _state.value.activeSlot
                ?: _state.value.lockedSlots?.singleOrNull()
        _state.update {
            it.copy(
                draftSearchName = query,
                searchName = query,
                slotCache = emptyMap(),
                candidates = emptyList(),
                extraMedia = emptyMap(),
                steamGridDbCandidate = null,
                mediaNextPage = emptyMap(),
                mediaHasMore = emptyMap(),
                error = null,
                message = null,
                slotError = null,
            )
        }
        if (active != null) {
            ensureSlotLoaded(active, force = true)
        }
    }

    fun openSlot(slot: ReviewSlot) {
        val locked = _state.value.lockedSlots
        if (locked != null && slot !in locked) return
        _state.update { it.copy(activeSlot = slot, slotError = null) }
        ensureSlotLoaded(slot)
    }

    fun closeSlot() {
        _state.update { it.copy(activeSlot = null, slotError = null) }
    }

    fun ensureSlotLoaded(
        slot: ReviewSlot,
        force: Boolean = false,
    ) {
        viewModelScope.launch { loadSlotIfNeeded(slot, force) }
    }

    private suspend fun loadSlotIfNeeded(
        slot: ReviewSlot,
        force: Boolean = false,
    ) {
        val state = _state.value
        val game = state.game ?: return
        val query = state.searchName.ifBlank { game.displayName }
        val cached = state.slotCache[slot]
        if (!force && cached != null && cached.searchName == query) return
        if (state.lockedSlots != null && slot !in state.lockedSlots) return

        _state.update {
            it.copy(slotLoading = true, slotError = null)
        }
        try {
            val settings = settingsRepository.current()
            when (slot) {
                ReviewSlot.Metadata -> {
                    val candidates = engine.gatherReviewCandidates(game, settings, query)
                    _state.update { cur ->
                        cur.copy(
                            slotLoading = false,
                            candidates = candidates,
                            slotCache =
                                cur.slotCache + (
                                    slot to
                                        SlotOptionsCache(
                                            searchName = query,
                                            metadataCandidates = candidates,
                                        )
                                ),
                            slotError =
                                if (candidates.isEmpty()) {
                                    "No matches found"
                                } else {
                                    null
                                },
                            preferredCandidate =
                                cur.preferredCandidate?.let { preferred ->
                                    candidates.firstOrNull { it.key() == preferred.key() }
                                        ?: preferred
                                },
                        )
                    }
                    val preferred = _state.value.preferredCandidate
                    if (preferred != null && _state.value.metadataFrom == null) {
                        selectCandidate(preferred, fillEmptyMediaOnly = true)
                    }
                }

                else -> {
                    val type = slot.requireMediaType()
                    val gathered = engine.gatherReviewMedia(game, type, settings, query)
                    val options = gathered.options
                    _state.update { cur ->
                        cur.copy(
                            slotLoading = false,
                            slotCache =
                                cur.slotCache + (
                                    slot to
                                        SlotOptionsCache(
                                            searchName = query,
                                            mediaOptions = options,
                                        )
                                ),
                            steamGridDbCandidate =
                                gathered.steamGridDbCandidate ?: cur.steamGridDbCandidate,
                            mediaNextPage = cur.mediaNextPage + (type to 1),
                            mediaHasMore =
                                cur.mediaHasMore + (type to gathered.steamGridDbHasMore),
                            slotError =
                                if (options.isEmpty()) {
                                    "No ${type.dbName} found"
                                } else {
                                    null
                                },
                        )
                    }
                }
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    slotLoading = false,
                    slotError = e.message ?: "Failed to load",
                )
            }
        }
    }

    fun selectCandidate(
        candidate: ScrapeCandidate,
        fillEmptyMediaOnly: Boolean = true,
    ) {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            val game = _state.value.game
            val effective =
                if (game != null) {
                    settings.forPlatform(game.platformId)
                } else {
                    settings
                }
            val locked = _state.value.lockedSlots
            val allowMetadata = locked == null || ReviewSlot.Metadata in locked
            val typesToFill =
                when {
                    locked == null -> ReviewMediaSlots
                    else -> locked.mapNotNull { it.mediaType() }
                }
            val defaults =
                typesToFill
                    .mapNotNull { type ->
                        val list = candidate.media.filter { it.type == type }
                        if (list.isEmpty()) return@mapNotNull null
                        val options =
                            list.map { media ->
                                RankedMediaOption(
                                    sourceId = candidate.sourceId,
                                    candidate = media,
                                    score = media.score ?: 0,
                                    authorKey = media.authorKey,
                                    authorDisplay = media.authorName,
                                    rankReason = "",
                                    confidence = candidate.matchConfidence,
                                )
                            }
                        val policy = MatchRankPolicy.fromSettings(effective, type)
                        val best =
                            MediaRanker.pickBestOption(
                                MediaRanker.rankMediaOptions(options, policy),
                                policy,
                            ) ?: return@mapNotNull null
                        type to StagedMediaPick(best.sourceId, best.candidate)
                    }.toMap()
            _state.update { cur ->
                val mergedMedia =
                    if (fillEmptyMediaOnly) {
                        cur.mediaPicks + defaults.filterKeys { it !in cur.mediaPicks }
                    } else {
                        defaults
                    }
                cur.copy(
                    selectedCandidateKey = if (allowMetadata) candidate.key() else cur.selectedCandidateKey,
                    metadataFrom = if (allowMetadata) candidate else cur.metadataFrom,
                    mediaPicks = mergedMedia,
                    error = null,
                    message = null,
                    activeSlot = if (cur.activeSlot == ReviewSlot.Metadata) null else cur.activeSlot,
                )
            }
        }
    }

    fun autoFillFromPriorities() {
        val state = _state.value
        if (state.lockedSlots != null) {
            val slot = state.lockedSlots.singleOrNull() ?: return
            val type = slot.mediaType() ?: return
            viewModelScope.launch {
                loadSlotIfNeeded(slot)
                val settings = settingsRepository.current()
                val game = _state.value.game
                val effective =
                    if (game != null) {
                        settings.forPlatform(game.platformId)
                    } else {
                        settings
                    }
                val options = _state.value.mediaOptions(type)
                if (options.isEmpty()) return@launch
                val policy = MatchRankPolicy.fromSettings(effective, type)
                val ranked =
                    MediaRanker.rankMediaOptions(
                        options.map { (sourceId, media) ->
                            RankedMediaOption(
                                sourceId = sourceId,
                                candidate = media,
                                score = media.score ?: 0,
                                authorKey = media.authorKey,
                                authorDisplay = media.authorName,
                                rankReason = "",
                                confidence = MatchConfidence.Unknown,
                            )
                        },
                        policy,
                    )
                val best = MediaRanker.pickBestOption(ranked, policy) ?: return@launch
                selectMedia(type, best.sourceId, best.candidate)
            }
            return
        }
        viewModelScope.launch {
            loadSlotIfNeeded(ReviewSlot.Metadata, force = false)
            val settings = settingsRepository.current()
            val game = _state.value.game
            val effective =
                if (game != null) {
                    settings.forPlatform(game.platformId)
                } else {
                    settings
                }
            var bySource = _state.value.metadataOptions().associateBy { it.sourceId }
            if (bySource.isEmpty() && game != null) {
                val candidates =
                    engine.gatherReviewCandidates(
                        game,
                        settings,
                        _state.value.searchName,
                    )
                bySource = candidates.associateBy { it.sourceId }
                _state.update {
                    it.copy(
                        candidates = candidates,
                        slotCache =
                            it.slotCache + (
                                ReviewSlot.Metadata to
                                    SlotOptionsCache(
                                        searchName = it.searchName,
                                        metadataCandidates = candidates,
                                    )
                            ),
                    )
                }
            }
            if (bySource.isEmpty()) return@launch
            val meta = pickMetadataCandidate(bySource, effective)
            val mediaPicks = pickMediaFromPriorities(bySource, effective)
            _state.update {
                it.copy(
                    selectedCandidateKey = meta?.key(),
                    metadataFrom = meta,
                    mediaPicks = mediaPicks,
                    message = "Auto-filled from source priorities",
                    messageSuccess = true,
                    error = null,
                )
            }
        }
    }

    fun loadMoreMedia(type: MediaType) {
        val state = _state.value
        if (state.mediaLoadingMore) return
        if (state.mediaHasMore[type] != true) return
        val game = state.game ?: return
        val sgdbCandidate =
            state.steamGridDbCandidate?.takeIf { it.sourceId == "steamgriddb" }
                ?: state.metadataFrom?.takeIf { it.sourceId == "steamgriddb" }
                ?: state.metadataOptions().firstOrNull { it.sourceId == "steamgriddb" }
                ?: state.candidates.firstOrNull { it.sourceId == "steamgriddb" }
        if (sgdbCandidate == null) {
            WajihaLog.w(
                WajihaTags.SCRAPE,
                "loadMoreMedia type=${type.dbName}: no SGDB game id — stopping paging",
            )
            _state.update { it.copy(mediaHasMore = it.mediaHasMore + (type to false)) }
            return
        }
        val page = state.mediaNextPage[type] ?: 1
        WajihaLog.i(
            WajihaTags.SCRAPE,
            "loadMoreMedia type=${type.dbName} page=$page gameId=${sgdbCandidate.sourceGameId}",
        )
        _state.update { it.copy(mediaLoadingMore = true, steamGridDbCandidate = sgdbCandidate) }
        viewModelScope.launch {
            try {
                val result =
                    engine.loadMoreSteamGridDbMedia(
                        candidate = sgdbCandidate,
                        type = type,
                        settings = settingsRepository.current(),
                        platformId = game.platformId,
                        page = page,
                    )
                val settings = settingsRepository.current().forPlatform(game.platformId)
                val filtered =
                    result.media.filterNot { media ->
                        MediaRanker.authorMatches(
                            media.authorKey,
                            media.authorName,
                            settings.blacklistedMediaAuthors,
                        )
                    }
                WajihaLog.i(
                    WajihaTags.SCRAPE,
                    "loadMoreMedia type=${type.dbName} page=$page " +
                        "got=${filtered.size} hasMore=${result.hasMore}",
                )
                _state.update { cur ->
                    val merged =
                        (cur.extraMedia[type].orEmpty() + filtered)
                            .distinctBy { it.url }
                    val slot = ReviewSlot.forMediaType(type)
                    val updatedCache =
                        if (slot != null) {
                            val prev = cur.slotCache[slot]
                            val morePairs = filtered.map { "steamgriddb" to it }
                            cur.slotCache + (
                                slot to
                                    SlotOptionsCache(
                                        searchName = cur.searchName,
                                        mediaOptions =
                                            (
                                                (prev?.mediaOptions.orEmpty()) + morePairs
                                            ).distinctBy { it.second.url },
                                    )
                            )
                        } else {
                            cur.slotCache
                        }
                    cur.copy(
                        mediaLoadingMore = false,
                        extraMedia = cur.extraMedia + (type to merged),
                        mediaNextPage = cur.mediaNextPage + (type to page + 1),
                        mediaHasMore = cur.mediaHasMore + (type to result.hasMore),
                        slotCache = updatedCache,
                    )
                }
            } catch (e: Exception) {
                WajihaLog.w(
                    WajihaTags.SCRAPE,
                    "loadMoreMedia type=${type.dbName} page=$page: ${e.message}",
                )
                _state.update {
                    it.copy(
                        mediaLoadingMore = false,
                        mediaHasMore = it.mediaHasMore + (type to false),
                        slotError = e.message ?: "Failed to load more media",
                    )
                }
            }
        }
    }

    fun clearMetadata() {
        _state.update {
            it.copy(metadataFrom = null, selectedCandidateKey = null)
        }
    }

    fun selectMedia(
        type: MediaType,
        sourceId: String,
        candidate: MediaCandidate?,
    ) {
        _state.update {
            it.copy(
                mediaPicks = it.mediaPicks + (type to StagedMediaPick(sourceId, candidate)),
                activeSlot = null,
            )
        }
    }

    fun clearSlot(type: MediaType) {
        _state.update {
            it.copy(mediaPicks = it.mediaPicks + (type to StagedMediaPick("manual", null)))
        }
    }

    fun leaveSlot(type: MediaType) {
        _state.update { it.copy(mediaPicks = it.mediaPicks - type) }
    }

    /** Yellow arrow — drop all staged picks. */
    fun revertStaged() {
        _state.update {
            it.copy(
                mediaPicks = emptyMap(),
                metadataFrom = null,
                selectedCandidateKey = null,
                message = null,
                error = null,
            )
        }
    }

    fun apply(onDone: (() -> Unit)? = null) {
        val game = _state.value.game ?: return
        val selection = _state.value.toSelection()
        _state.update { it.copy(applying = true, error = null, message = null) }
        viewModelScope.launch {
            try {
                val result =
                    engine.applySelection(
                        game,
                        selection,
                        settingsRepository.current(),
                    )
                val (msg, ok) = result.userMessage(verb = "Applied")
                _state.update {
                    it.copy(
                        applying = false,
                        message = msg,
                        messageSuccess = ok,
                    )
                }
                advanceOrFinish(onDone)
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        applying = false,
                        error = e.message ?: "Apply failed",
                        messageSuccess = false,
                    )
                }
            }
        }
    }

    fun skip(onDone: (() -> Unit)? = null) {
        advanceOrFinish(onDone)
    }

    fun scrapeOneShot(
        game: GameEntity,
        policy: ScrapeRunPolicy,
        onDone: ((String, Boolean) -> Unit)? = null,
    ) {
        viewModelScope.launch {
            try {
                val result = engine.scrapeGame(game, settingsRepository.current(), policy)
                val (msg, ok) = result.userMessage(verb = "Scraped")
                onDone?.invoke(
                    msg,
                    ok || result.outcome == com.wajiha.data.scraper.GameScrapeOutcome.Partial,
                )
            } catch (e: Exception) {
                onDone?.invoke(e.message ?: "Scrape failed", false)
            }
        }
    }

    private fun advanceOrFinish(onDone: (() -> Unit)?) {
        if (queueIndex >= queue.lastIndex) {
            onDone?.invoke()
            onQueueFinished?.invoke()
            return
        }
        queueIndex++
        loadCurrent()
    }

    private fun loadCurrent(
        lockedSlots: Set<ReviewSlot>? = null,
        preferredCandidate: ScrapeCandidate? = null,
        openPickerImmediately: Boolean = false,
    ) {
        val game = queue.getOrNull(queueIndex) ?: return
        val locked = lockedSlots
        _state.value =
            ScrapeReviewState(
                game = game,
                searchName = game.displayName,
                draftSearchName = game.displayName,
                loading = true,
                queueIndex = queueIndex,
                queueTotal = queue.size,
                lockedSlots = locked,
                preferredCandidate = preferredCandidate,
                activeSlot =
                    if (openPickerImmediately) {
                        locked?.firstOrNull()
                    } else {
                        null
                    },
            )
        viewModelScope.launch {
            try {
                val existing = gameRepository.media(game.id)
                _state.update {
                    it.copy(
                        game = gameRepository.byId(game.id) ?: game,
                        existingMedia = existing,
                        loading = false,
                    )
                }
                if (preferredCandidate != null &&
                    (locked == null || ReviewSlot.Metadata in locked)
                ) {
                    ensureSlotLoaded(ReviewSlot.Metadata)
                } else if (openPickerImmediately) {
                    locked?.firstOrNull()?.let { ensureSlotLoaded(it) }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: "Failed to load game",
                        game = game,
                    )
                }
            }
        }
    }

    private fun pickMetadataCandidate(
        bySource: Map<String, ScrapeCandidate>,
        settings: ScraperSettings,
    ): ScrapeCandidate? {
        for (sourceId in settings.metadataPriority) {
            val c = bySource[sourceId] ?: continue
            if (c.metadata != null) return c
        }
        return bySource.values.firstOrNull { it.metadata != null }
            ?: bySource.values.firstOrNull()
    }

    private fun pickMediaFromPriorities(
        bySource: Map<String, ScrapeCandidate>,
        settings: ScraperSettings,
    ): Map<MediaType, StagedMediaPick?> {
        val picks = mutableMapOf<MediaType, StagedMediaPick?>()
        for (type in ReviewMediaSlots) {
            val options =
                bySource.values.flatMap { c ->
                    c.media.filter { it.type == type }.map { media ->
                        RankedMediaOption(
                            sourceId = c.sourceId,
                            candidate = media,
                            score = media.score ?: 0,
                            authorKey = media.authorKey,
                            authorDisplay = media.authorName,
                            rankReason = "",
                            confidence = c.matchConfidence,
                        )
                    }
                }
            if (options.isEmpty()) continue
            val policy = MatchRankPolicy.fromSettings(settings, type)
            val ranked = MediaRanker.rankMediaOptions(options, policy)
            val best = MediaRanker.pickBestOption(ranked, policy) ?: continue
            picks[type] = StagedMediaPick(best.sourceId, best.candidate)
        }
        return picks
    }
}
