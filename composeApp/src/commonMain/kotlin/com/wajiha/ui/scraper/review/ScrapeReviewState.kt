package com.wajiha.ui.scraper.review

import com.wajiha.data.db.GameEntity
import com.wajiha.data.db.GameMediaEntity
import com.wajiha.data.scraper.MediaCandidate
import com.wajiha.data.scraper.MediaType
import com.wajiha.data.scraper.ScrapeCandidate
import com.wajiha.data.scraper.ScrapeSelection
import com.wajiha.data.scraper.StagedMediaPick

/** Overview / picker slot — metadata match or one media type. */
enum class ReviewSlot {
    Metadata,
    Boxart,
    Logo,
    Hero,
    Screenshot,
    Fanart,
    Banner,
    Icon;

    fun mediaType(): MediaType? = when (this) {
        Metadata -> null
        Boxart -> MediaType.Boxart
        Logo -> MediaType.Logo
        Hero -> MediaType.Hero
        Screenshot -> MediaType.Screenshot
        Fanart -> MediaType.Fanart
        Banner -> MediaType.Banner
        Icon -> MediaType.Icon
    }

    fun requireMediaType(): MediaType = mediaType()
        ?: error("ReviewSlot.Metadata has no media type")

    fun label(): String = when (this) {
        Metadata -> "Metadata"
        Boxart -> "Box art"
        Logo -> "Logo"
        Hero -> "Hero"
        Screenshot -> "Screenshots"
        Fanart -> "Fan art"
        Banner -> "Banner"
        Icon -> "Icon"
    }

    companion object {
        fun forMediaType(type: MediaType): ReviewSlot? = entries.firstOrNull {
            it.mediaType() == type
        }
    }
}

/** Cached options for one slot under one search query. */
data class SlotOptionsCache(
    val searchName: String,
    val metadataCandidates: List<ScrapeCandidate> = emptyList(),
    val mediaOptions: List<Pair<String, MediaCandidate>> = emptyList()
)

/** Interactive review picker state for one game (or one queue item). */
data class ScrapeReviewState(
    val game: GameEntity? = null,
    val existingMedia: List<GameMediaEntity> = emptyList(),
    /** Last applied search query (drives candidate list). */
    val searchName: String = "",
    /** Editable draft; only applied when Search is pressed. */
    val draftSearchName: String = "",
    val candidates: List<ScrapeCandidate> = emptyList(),
    val selectedCandidateKey: String? = null,
    val metadataFrom: ScrapeCandidate? = null,
    /**
     * Present key = staged; value null = clear that slot;
     * value with candidate = download from that source.
     */
    val mediaPicks: Map<MediaType, StagedMediaPick?> = emptyMap(),
    /**
     * Extra SteamGridDB pages merged into the gallery (beyond the first search page).
     * Keyed by media type.
     */
    val extraMedia: Map<MediaType, List<MediaCandidate>> = emptyMap(),
    /** Next SGDB page index to request per type (0 already in candidate.media). */
    val mediaNextPage: Map<MediaType, Int> = emptyMap(),
    val mediaHasMore: Map<MediaType, Boolean> = emptyMap(),
    val mediaLoadingMore: Boolean = false,
    /** Initial load for a queue item (existing media only). */
    val loading: Boolean = false,
    /** Soft refresh / slot option load. */
    val slotLoading: Boolean = false,
    val applying: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val messageSuccess: Boolean? = null,
    /** Queue progress when reviewing a platform batch. */
    val queueIndex: Int = 0,
    val queueTotal: Int = 0,
    /** null = full overview; non-null = single-slot Manual. */
    val lockedSlots: Set<ReviewSlot>? = null,
    /** Open slot picker when non-null. */
    val activeSlot: ReviewSlot? = null,
    /** Per-slot option cache keyed by slot; valid for [searchName]. */
    val slotCache: Map<ReviewSlot, SlotOptionsCache> = emptyMap(),
    val slotError: String? = null,
    val preferredCandidate: ScrapeCandidate? = null
) {
    val selectedCandidate: ScrapeCandidate?
        get() = candidates.firstOrNull { it.key() == selectedCandidateKey }
            ?: metadataFrom

    val hasChanges: Boolean
        get() = mediaPicks.isNotEmpty() || metadataFrom != null

    val visibleSlots: List<ReviewSlot>
        get() = lockedSlots?.toList() ?: ReviewSlot.entries

    fun mediaOptions(type: MediaType): List<Pair<String, MediaCandidate>> {
        val slot = ReviewSlot.forMediaType(type)
        val cached = slot?.let { slotCache[it] }
            ?.takeIf { it.searchName == searchName }
            ?.mediaOptions
            .orEmpty()
        val fromCandidates = candidates.flatMap { c ->
            c.media.filter { it.type == type }.map { c.sourceId to it }
        }
        val fromExtra = (extraMedia[type].orEmpty()).map { "steamgriddb" to it }
        return (cached + fromCandidates + fromExtra).distinctBy { it.second.url }
    }

    fun metadataOptions(): List<ScrapeCandidate> {
        val cached = slotCache[ReviewSlot.Metadata]
            ?.takeIf { it.searchName == searchName }
            ?.metadataCandidates
            .orEmpty()
        return (cached + candidates).distinctBy { it.key() }
    }

    fun hasExistingMedia(type: MediaType): Boolean =
        existingMedia.any { it.type == type.dbName && !it.localPath.isNullOrBlank() }

    fun existingPath(type: MediaType): String? =
        existingMedia.firstOrNull { it.type == type.dbName }?.localPath

    fun stagedOrExistingUrl(type: MediaType): String? {
        val staged = mediaPicks[type]
        if (type in mediaPicks) {
            return staged?.candidate?.url
        }
        return existingPath(type)
    }

    fun toSelection(): ScrapeSelection = ScrapeSelection(
        metadataFrom = metadataFrom,
        media = mediaPicks
    )
}

fun ScrapeCandidate.key(): String = "$sourceId::$sourceGameId"

/** Media types shown as editable slots in the review picker. */
val ReviewMediaSlots: List<MediaType> = listOf(
    MediaType.Boxart,
    MediaType.Logo,
    MediaType.Hero,
    MediaType.Screenshot,
    MediaType.Fanart,
    MediaType.Banner,
    MediaType.Icon
)
