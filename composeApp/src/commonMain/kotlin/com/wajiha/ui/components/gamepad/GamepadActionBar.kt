package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wajiha.data.prefs.AppSettings
import com.wajiha.data.prefs.SettingsRepository
import com.wajiha.input.ControllerGlyphLabels
import com.wajiha.input.ControllerGlyphStore
import com.wajiha.input.GamepadHint
import com.wajiha.input.GamepadHintButton
import com.wajiha.ui.theme.WajihaSpacing
import org.koin.compose.koinInject

/** Fixed chrome height: one labelMedium line + Kenney glyph (~20dp). */
val GamepadActionBarHeight = 36.dp

private val GlyphSize = 20.dp

fun defaultGamepadHints(isDual: Boolean = true): List<GamepadHint> =
    buildList {
        add(GamepadHint(GamepadHintButton.A, "Confirm"))
        add(GamepadHint(GamepadHintButton.B, "Back"))
        add(GamepadHint(GamepadHintButton.L1R1, "Section"))
        if (isDual) {
            add(GamepadHint(GamepadHintButton.L2, "Focus screen"))
        }
        add(GamepadHint(GamepadHintButton.R2, "Notifications"))
        if (isDual) {
            add(GamepadHint(GamepadHintButton.Select, "Swap screens"))
        }
    }

val quickSettingsGamepadHints: List<GamepadHint> =
    listOf(
        GamepadHint(GamepadHintButton.A, "Toggle"),
        GamepadHint(GamepadHintButton.DpadLeftRight, "Adjust slider"),
    )

val runningAppsGamepadHints: List<GamepadHint> =
    listOf(
        GamepadHint(GamepadHintButton.A, "Action"),
        GamepadHint(GamepadHintButton.B, "Back to grid"),
    )

val achievementsGamepadHints: List<GamepadHint> =
    listOf(
        GamepadHint(GamepadHintButton.B, "Back"),
    )

fun settingsGamepadHints(isDual: Boolean = true): List<GamepadHint> =
    buildList {
        add(GamepadHint(GamepadHintButton.A, "Select/Toggle"))
        add(GamepadHint(GamepadHintButton.B, "Back"))
        add(GamepadHint(GamepadHintButton.Y, "Reset"))
        add(GamepadHint(GamepadHintButton.L1R1, "Section"))
        if (isDual) {
            add(GamepadHint(GamepadHintButton.L2, "Focus screen"))
        }
    }

fun libraryPlatformRowGamepadHints(isDual: Boolean = true): List<GamepadHint> =
    buildList {
        add(GamepadHint(GamepadHintButton.A, "Rescan"))
        add(GamepadHint(GamepadHintButton.X, "Edit"))
        add(GamepadHint(GamepadHintButton.B, "Back"))
        add(GamepadHint(GamepadHintButton.L1R1, "Section"))
        if (isDual) {
            add(GamepadHint(GamepadHintButton.L2, "Focus screen"))
        }
    }

fun libraryChromeGamepadHints(isDual: Boolean = true): List<GamepadHint> =
    buildList {
        add(GamepadHint(GamepadHintButton.A, "Select"))
        add(GamepadHint(GamepadHintButton.B, "Back"))
        add(GamepadHint(GamepadHintButton.L1R1, "Section"))
        if (isDual) {
            add(GamepadHint(GamepadHintButton.L2, "Focus screen"))
        }
    }

fun gameDetailGamepadHints(isDual: Boolean = true): List<GamepadHint> =
    buildList {
        add(GamepadHint(GamepadHintButton.A, "Select/Launch"))
        add(GamepadHint(GamepadHintButton.B, "Back"))
        add(GamepadHint(GamepadHintButton.Y, "Reset"))
        add(GamepadHint(GamepadHintButton.L1R1, "Tab"))
        if (isDual) {
            add(GamepadHint(GamepadHintButton.L2, "Focus screen"))
        }
    }

/** Drop L2 Focus / SELECT Swap hints when not in dual layout. */
fun List<GamepadHint>.withoutDualScreenChrome(isDual: Boolean): List<GamepadHint> {
    if (isDual) return this
    return filterNot { hint ->
        hint.button == GamepadHintButton.L2 ||
            (
                hint.button == GamepadHintButton.Select &&
                    hint.action.contains("Swap", ignoreCase = true)
            )
    }
}

val secondaryModeTabGamepadHints: List<GamepadHint> =
    listOf(
        GamepadHint(GamepadHintButton.A, "Select tab"),
        GamepadHint(GamepadHintButton.B, "Games"),
        GamepadHint(GamepadHintButton.L1R1, "Tab"),
    )

@Composable
fun GamepadActionBar(
    modifier: Modifier = Modifier,
    hints: List<GamepadHint> = defaultGamepadHints(),
    glyphStore: ControllerGlyphStore = koinInject(),
    settingsRepository: SettingsRepository = koinInject(),
) {
    val settings by settingsRepository.settings.collectAsState(
        initial = AppSettings(),
    )
    val devices by glyphStore.devices.collectAsState()
    val showHints =
        remember(settings.controllerGlyphScheme, devices.size) {
            ControllerGlyphLabels.shouldShowActionBarHints(
                schemePref = settings.controllerGlyphScheme,
                connectedDeviceCount = devices.size,
            )
        }
    if (!showHints) return

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(GamepadActionBarHeight)
                .clipToBounds()
                .padding(horizontal = WajihaSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        hints.forEach { hint ->
            GamepadHintChip(
                hint = hint,
                // fill=true so many hints share width evenly and truncate instead of overflowing.
                modifier = Modifier.weight(1f, fill = true),
            )
        }
    }
}

@Composable
private fun GamepadHintChip(
    hint: GamepadHint,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GamepadHintGlyph(
            button = hint.button,
            size = GlyphSize,
        )
        Text(
            text = hint.action,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
