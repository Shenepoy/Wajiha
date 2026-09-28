package com.wajiha.data.scraper

import com.wajiha.data.db.GameEntity
import com.wajiha.data.db.GameMediaEntity

/**
 * Per-run scrape policy for WorkManager batches.
 * Interactive Review is UI-only and does not use the worker.
 */
enum class ScrapeRunMode {
    FillGaps,
    Force,
}

data class ScrapeRunPolicy(
    val mode: ScrapeRunMode = ScrapeRunMode.FillGaps,
    val overwriteMetadata: Boolean = false,
    val overwriteMedia: Boolean = false,
    val onlyMissingMedia: Boolean = true,
    /**
     * Optional per-run source filter. Null/empty uses the enabled source list.
     * One or more source ids (e.g. `screenscraper`, `steamgriddb`).
     */
    val sourceIds: List<String>? = null,
) {
    /** Single-source shorthand when exactly one id is selected. */
    val sourceId: String?
        get() = sourceIds?.singleOrNull()

    companion object {
        val FillGaps = ScrapeRunPolicy()
        val Force =
            ScrapeRunPolicy(
                mode = ScrapeRunMode.Force,
                overwriteMetadata = true,
                overwriteMedia = true,
                onlyMissingMedia = false,
            )

        fun fromName(name: String?): ScrapeRunPolicy {
            val parts = name.orEmpty().lowercase().split(':', limit = 2)
            val base = if (parts.firstOrNull() == "force") Force else FillGaps
            val ids =
                parts
                    .getOrNull(1)
                    ?.split(',')
                    ?.map { it.trim() }
                    ?.filter { it.isNotBlank() }
                    ?.distinct()
            return base.copy(sourceIds = ids?.takeIf { it.isNotEmpty() })
        }
    }

    fun wireName(): String {
        val modeName =
            when (mode) {
                ScrapeRunMode.FillGaps -> "fill_gaps"
                ScrapeRunMode.Force -> "force"
            }
        val ids = sourceIds.orEmpty().filter { it.isNotBlank() }
        return if (ids.isEmpty()) modeName else "$modeName:${ids.joinToString(",")}"
    }

    /** Restrict [settings] enabled sources for this run, if a filter is set. */
    fun applySourceFilter(settings: ScraperSettings): ScraperSettings {
        val ids =
            sourceIds
                .orEmpty()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
        if (ids.isEmpty()) return settings
        return settings.copy(enabledSources = ids)
    }
}

/**
 * Staged picks from the interactive review picker.
 *
 * - [metadataFrom] null = keep existing metadata
 * - [media] absent key = don't touch that type
 * - [media] present with null [StagedMediaPick.candidate] = clear/delete that type
 * - [media] present with candidate = download and save from [StagedMediaPick.sourceId]
 */
data class StagedMediaPick(
    val sourceId: String,
    val candidate: MediaCandidate?,
)

data class ScrapeSelection(
    val metadataFrom: ScrapeCandidate? = null,
    val media: Map<MediaType, StagedMediaPick?> = emptyMap(),
)

/** Preferred media types used to decide if a game still has gaps. */
val GapFillMediaTypes: List<MediaType> =
    listOf(
        MediaType.Boxart,
        MediaType.Square,
        MediaType.Logo,
        MediaType.Hero,
    )

fun GameEntity.needsGapFill(existingMedia: List<GameMediaEntity>): Boolean {
    if (scrapedAt == null) return true
    val byType = existingMedia.associateBy { it.type }
    return GapFillMediaTypes.any { type ->
        byType[type.dbName]?.localPath.isNullOrBlank()
    }
}

fun List<GameMediaEntity>.missingGapTypes(): List<MediaType> {
    val byType = associateBy { it.type }
    return GapFillMediaTypes.filter { type ->
        byType[type.dbName]?.localPath.isNullOrBlank()
    }
}
