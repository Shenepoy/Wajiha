package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wajiha.data.prefs.AppSettings
import com.wajiha.data.prefs.SettingsRepository
import com.wajiha.input.ControllerGlyphLabels
import com.wajiha.input.ControllerGlyphStore
import com.wajiha.input.GamepadHint
import com.wajiha.input.GamepadHintButton
import com.wajiha.input.GamepadTextEditRegistry
import com.wajiha.ui.theme.WajihaColors
import com.wajiha.ui.theme.WajihaSpacing
import org.koin.compose.koinInject

/**
 * Chrome behind [GamepadActionBar].
 *
 * - [Solid]: opaque surface for Settings / System / menu (default).
 * - [Overlay]: fade-to-scrim over full-bleed hero artwork (no layout crop).
 */
enum class GamepadActionBarChrome {
    Solid,
    Overlay,
}

/** Fixed chrome height: one labelMedium line + Kenney glyph (~20dp). */
val GamepadActionBarHeight = 36.dp

private val GlyphSize = 20.dp

/** L2 cross-display focus switch — injected by [com.wajiha.input.MirroredOrLocalGamepadActionBar]. */
val FocusScreenGamepadHint = GamepadHint(GamepadHintButton.L2, "Focus")

/** Shown while [GamepadTextEditRegistry.isEditing] — replaces screen hints. */
val textEditingGamepadHints: List<GamepadHint> =
    listOf(
        GamepadHint(GamepadHintButton.A, "Done"),
        GamepadHint(GamepadHintButton.B, "Close keyboard"),
    )

fun defaultGamepadHints(isDual: Boolean = true): List<GamepadHint> =
    buildList {
        add(GamepadHint(GamepadHintButton.A, "Confirm"))
        add(GamepadHint(GamepadHintButton.B, "Back"))
        add(GamepadHint(GamepadHintButton.L1R1, "Section"))
        add(GamepadHint(GamepadHintButton.R2, "Notifications"))
        if (isDual) {
            add(GamepadHint(GamepadHintButton.Select, "Swap screens"))
        }
    }

val quickSettingsGamepadHints: List<GamepadHint> =
    listOf(
        GamepadHint(GamepadHintButton.A, "Activate / Adjust"),
        GamepadHint(GamepadHintButton.DpadLeftRight, "Adjust"),
        GamepadHint(GamepadHintButton.B, "Back"),
    )

val runningAppsGamepadHints: List<GamepadHint> =
    listOf(
        GamepadHint(GamepadHintButton.A, "Front / Top"),
        GamepadHint(GamepadHintButton.X, "Kill"),
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
    }

fun libraryPlatformRowGamepadHints(isDual: Boolean = true): List<GamepadHint> =
    buildList {
        add(GamepadHint(GamepadHintButton.A, "Rescan"))
        add(GamepadHint(GamepadHintButton.X, "Edit"))
        add(GamepadHint(GamepadHintButton.B, "Back"))
        add(GamepadHint(GamepadHintButton.L1R1, "Section"))
    }

fun libraryChromeGamepadHints(isDual: Boolean = true): List<GamepadHint> =
    buildList {
        add(GamepadHint(GamepadHintButton.A, "Select"))
        add(GamepadHint(GamepadHintButton.B, "Back"))
        add(GamepadHint(GamepadHintButton.L1R1, "Section"))
    }

fun gameDetailGamepadHints(isDual: Boolean = true): List<GamepadHint> =
    buildList {
        add(GamepadHint(GamepadHintButton.A, "Select/Launch"))
        add(GamepadHint(GamepadHintButton.B, "Back"))
        add(GamepadHint(GamepadHintButton.Y, "Reset"))
        add(GamepadHint(GamepadHintButton.L1R1, "Tab"))
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

/** Strip any L2 Focus hint — re-injected at the action-bar edge when needed. */
fun List<GamepadHint>.withoutFocusScreenHint(): List<GamepadHint> = filterNot { it.button == GamepadHintButton.L2 }

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
    chrome: GamepadActionBarChrome = GamepadActionBarChrome.Solid,
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

    val effectiveHints =
        if (GamepadTextEditRegistry.isEditing) textEditingGamepadHints else hints
    val labelColor =
        when (chrome) {
            GamepadActionBarChrome.Solid -> MaterialTheme.colorScheme.onSurfaceVariant
            GamepadActionBarChrome.Overlay -> Color.White.copy(alpha = 0.92f)
        }
    val chromeModifier =
        when (chrome) {
            GamepadActionBarChrome.Solid -> {
                Modifier.background(MaterialTheme.colorScheme.background)
            }

            // Soft bottom fade so hero art stays full-bleed under mirrored hints.
            GamepadActionBarChrome.Overlay -> {
                Modifier.background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                Color.Transparent,
                                WajihaColors.HeroScrim,
                            ),
                    ),
                )
            }
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(GamepadActionBarHeight)
                .then(chromeModifier)
                .clipToBounds()
                .padding(horizontal = WajihaSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        effectiveHints.forEach { hint ->
            GamepadHintChip(
                hint = hint,
                labelColor = labelColor,
                // fill=true so many hints share width evenly and truncate instead of overflowing.
                modifier = Modifier.weight(1f, fill = true),
            )
        }
    }
}

/**
 * Floating L2 "Focus" chip for the hero display — does not reserve action-bar
 * height or push hero content.
 */
@Composable
fun FocusScreenHintOverlay(
    modifier: Modifier = Modifier,
    glyphStore: ControllerGlyphStore = koinInject(),
    settingsRepository: SettingsRepository = koinInject(),
) {
    val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
    val devices by glyphStore.devices.collectAsState()
    val showHints =
        remember(settings.controllerGlyphScheme, devices.size) {
            ControllerGlyphLabels.shouldShowActionBarHints(
                schemePref = settings.controllerGlyphScheme,
                connectedDeviceCount = devices.size,
            )
        }
    if (!showHints || GamepadTextEditRegistry.isEditing) return
    GamepadHintChip(
        hint = FocusScreenGamepadHint,
        modifier = modifier.padding(WajihaSpacing.md),
    )
}

@Composable
private fun GamepadHintChip(
    hint: GamepadHint,
    modifier: Modifier = Modifier,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            color = labelColor,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
