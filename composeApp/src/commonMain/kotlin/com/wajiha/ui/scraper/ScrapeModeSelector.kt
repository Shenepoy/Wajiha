package com.wajiha.ui.scraper

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.wajiha.data.scraper.ScrapeRunMode
import com.wajiha.data.scraper.ScrapeRunPolicy
import com.wajiha.ui.components.gamepad.GamepadSettingRow
import com.wajiha.ui.components.gamepad.SettingType
import com.wajiha.ui.components.gamepad.SettingsTrailingActionButton

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
        ScrapeUiMode.FillGaps -> "Only fetch missing boxart, square, logo, or hero."
        ScrapeUiMode.Force -> "Overwrite metadata and media from all sources."
        ScrapeUiMode.Review -> "Pick match and artwork from every source, then apply."
    }

fun ScrapeUiMode.singleGameChipLabel(): String =
    when (this) {
        ScrapeUiMode.FillGaps -> "Scrape"
        ScrapeUiMode.Force -> "Force"
        ScrapeUiMode.Review -> "Manual"
    }

fun ScrapeUiMode.batchChipLabel(): String =
    when (this) {
        ScrapeUiMode.FillGaps -> "Fill gaps"
        ScrapeUiMode.Force -> "Force"
        ScrapeUiMode.Review -> "Manual"
    }

/**
 * Mode chips + trailing Run on one settings row.
 * L/R changes mode; A or the trailing button starts the run.
 */
@Composable
fun ScrapeModeSelector(
    selected: ScrapeUiMode,
    onSelect: (ScrapeUiMode) -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String = "Run",
    showReview: Boolean = true,
    firstFocusRequester: FocusRequester? = null,
    enabled: Boolean = true,
    actionEnabled: Boolean = enabled,
    helperText: String = selected.helperText(),
    /** When true, choices read Scrape | Force | Manual (game detail). */
    singleGameLabels: Boolean = false,
) {
    val modes =
        if (showReview) {
            ScrapeUiMode.entries
        } else {
            listOf(ScrapeUiMode.FillGaps, ScrapeUiMode.Force)
        }
    val options =
        modes.map { mode ->
            mode.name to
                if (singleGameLabels) {
                    mode.singleGameChipLabel()
                } else {
                    mode.batchChipLabel()
                }
        }
    GamepadSettingRow(
        label = "Mode",
        description = helperText,
        type = SettingType.BinaryChoice,
        options = options,
        selected = selected.name,
        onSelect = { id ->
            if (!enabled) return@GamepadSettingRow
            modes.firstOrNull { it.name == id }?.let(onSelect)
        },
        onActivate = if (actionEnabled) onAction else null,
        onReset = {
            if (enabled) onSelect(ScrapeUiMode.FillGaps)
        },
        isAtDefault = selected == ScrapeUiMode.FillGaps,
        focusRequester = firstFocusRequester,
        modifier = modifier.fillMaxWidth(),
        content = {
            SettingsTrailingActionButton(
                text = actionLabel,
                onClick = onAction,
                enabled = actionEnabled,
            )
        },
    )
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
