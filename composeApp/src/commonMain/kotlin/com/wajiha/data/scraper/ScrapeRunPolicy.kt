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
    /** Optional per-run source override. Null uses the enabled source list. */
    val sourceId: String? = null,
) {
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
            return base.copy(sourceId = parts.getOrNull(1)?.takeIf(String::isNotBlank))
        }
    }

    fun wireName(): String {
        val modeName =
            when (mode) {
                ScrapeRunMode.FillGaps -> "fill_gaps"
                ScrapeRunMode.Force -> "force"
            }
        return sourceId?.takeIf(String::isNotBlank)?.let { "$modeName:$it" } ?: modeName
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
