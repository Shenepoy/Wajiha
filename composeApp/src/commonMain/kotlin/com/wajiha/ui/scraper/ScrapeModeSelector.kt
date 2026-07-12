package com.wajiha.ui.scraper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.wajiha.data.scraper.ScrapeRunMode
import com.wajiha.data.scraper.ScrapeRunPolicy
import com.wajiha.ui.components.gamepad.GamepadChip
import com.wajiha.ui.theme.WajihaSpacing

/** UI mode including interactive Review (not a WorkManager policy). */
enum class ScrapeUiMode {
    FillGaps,
    Force,
    Review,
}

fun ScrapeUiMode.toPolicy(): ScrapeRunPolicy =
    when (this) {
        ScrapeUiMode.FillGaps -> ScrapeRunPolicy.FillGaps
        ScrapeUiMode.Force -> ScrapeRunPolicy.Force
        ScrapeUiMode.Review -> ScrapeRunPolicy.FillGaps
    }

fun ScrapeUiMode.helperText(): String =
    when (this) {
        ScrapeUiMode.FillGaps -> "Only games missing metadata or preferred artwork."
        ScrapeUiMode.Force -> "Re-scrape everything and overwrite metadata and media."
        ScrapeUiMode.Review -> "Look up candidates, then approve or skip each game."
    }

/** Single-game copy for game detail (not batch). */
fun ScrapeUiMode.singleGameHelperText(): String =
    when (this) {
        ScrapeUiMode.FillGaps -> "Only fetch missing boxart, logo, or hero."
        ScrapeUiMode.Force -> "Overwrite metadata and media from all sources."
        ScrapeUiMode.Review -> "Pick match and artwork from every source, then apply."
    }

fun ScrapeUiMode.singleGameChipLabel(): String =
    when (this) {
        ScrapeUiMode.FillGaps -> "Scrape"
        ScrapeUiMode.Force -> "Force"
        ScrapeUiMode.Review -> "Manual"
    }

@Composable
fun ScrapeModeSelector(
    selected: ScrapeUiMode,
    onSelect: (ScrapeUiMode) -> Unit,
    modifier: Modifier = Modifier,
    showReview: Boolean = true,
    firstFocusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    helperText: String = selected.helperText(),
    /** When true, chips read Scrape | Force | Manual (game detail). */
    singleGameLabels: Boolean = false,
) {
    val modes =
        if (showReview) {
            ScrapeUiMode.entries
        } else {
            listOf(ScrapeUiMode.FillGaps, ScrapeUiMode.Force)
        }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WajihaSpacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
        ) {
            modes.forEachIndexed { index, mode ->
                GamepadChip(
                    label =
                        if (singleGameLabels) {
                            mode.singleGameChipLabel()
                        } else {
                            when (mode) {
                                ScrapeUiMode.FillGaps -> "Fill gaps"
                                ScrapeUiMode.Force -> "Force"
                                ScrapeUiMode.Review -> "Review"
                            }
                        },
                    selected = selected == mode,
                    onClick = { if (enabled) onSelect(mode) },
                    focusRequester = if (index == 0) firstFocusRequester else null,
                )
            }
        }
        Text(
            text = helperText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Maps batch policy mode for estimates (Review uses FillGaps filter by default). */
fun ScrapeUiMode.batchPolicyOrNull(): ScrapeRunPolicy? =
    when (this) {
        ScrapeUiMode.FillGaps -> ScrapeRunPolicy.FillGaps
        ScrapeUiMode.Force -> ScrapeRunPolicy.Force
        ScrapeUiMode.Review -> null
    }

fun ScrapeRunMode.toUiMode(): ScrapeUiMode =
    when (this) {
        ScrapeRunMode.FillGaps -> ScrapeUiMode.FillGaps
        ScrapeRunMode.Force -> ScrapeUiMode.Force
    }
