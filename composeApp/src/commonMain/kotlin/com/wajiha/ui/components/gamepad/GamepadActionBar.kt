package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wajiha.data.prefs.AppSettings
import com.wajiha.data.prefs.SettingsRepository
import com.wajiha.input.ControllerGlyphAssets
import com.wajiha.input.ControllerGlyphFaceStyle
import com.wajiha.input.ControllerGlyphLabels
import com.wajiha.input.ControllerGlyphOtherStyle
import com.wajiha.input.ControllerGlyphScheme
import com.wajiha.input.ControllerGlyphStore
import com.wajiha.input.GamepadHint
import com.wajiha.input.GamepadHintButton
import com.wajiha.input.isFaceGlyphButton
import com.wajiha.input.withEnclosedTransparencyFilled
import com.wajiha.ui.theme.WajihaSpacing
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

/** Fixed chrome height: one labelMedium line + Kenney glyph (~20dp). */
val GamepadActionBarHeight = 36.dp

private val GlyphSize = 20.dp

fun defaultGamepadHints(): List<GamepadHint> =
    listOf(
        GamepadHint(GamepadHintButton.A, "Confirm"),
        GamepadHint(GamepadHintButton.B, "Back"),
        GamepadHint(GamepadHintButton.L1R1, "Section"),
        GamepadHint(GamepadHintButton.L2, "Focus screen"),
        GamepadHint(GamepadHintButton.R2, "Notifications"),
        GamepadHint(GamepadHintButton.Select, "Swap screens"),
    )

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

val settingsGamepadHints: List<GamepadHint> =
    listOf(
        GamepadHint(GamepadHintButton.A, "Select/Toggle"),
        GamepadHint(GamepadHintButton.B, "Back"),
        GamepadHint(GamepadHintButton.Y, "Reset"),
        GamepadHint(GamepadHintButton.L1R1, "Section"),
        GamepadHint(GamepadHintButton.L2, "Focus screen"),
    )

val gameDetailGamepadHints: List<GamepadHint> =
    listOf(
        GamepadHint(GamepadHintButton.A, "Select/Launch"),
        GamepadHint(GamepadHintButton.B, "Back"),
        GamepadHint(GamepadHintButton.Y, "Reset"),
        GamepadHint(GamepadHintButton.L1R1, "Tab"),
        GamepadHint(GamepadHintButton.L2, "Focus screen"),
    )

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
    val lastInputType by glyphStore.lastInputType.collectAsState()
    val showHints =
        remember(settings.controllerGlyphScheme, devices.size) {
            ControllerGlyphLabels.shouldShowActionBarHints(
                schemePref = settings.controllerGlyphScheme,
                connectedDeviceCount = devices.size,
            )
        }
    if (!showHints) return

    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val faceStyle =
        remember(settings.controllerGlyphFaceStyle) {
            ControllerGlyphFaceStyle.fromName(settings.controllerGlyphFaceStyle)
        }
    val otherStyle =
        remember(settings.controllerGlyphOtherStyle) {
            ControllerGlyphOtherStyle.fromName(settings.controllerGlyphOtherStyle)
        }
    val scheme =
        remember(
            settings.controllerGlyphsEnabled,
            settings.controllerGlyphScheme,
            lastInputType,
        ) {
            ControllerGlyphLabels.resolveEffectiveScheme(
                glyphsEnabled = settings.controllerGlyphsEnabled,
                schemePref = settings.controllerGlyphScheme,
                lastInputType = lastInputType,
            )
        }

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
                scheme = scheme,
                faceStyle = faceStyle,
                otherStyle = otherStyle,
                darkTheme = darkTheme,
                // fill=true so many hints share width evenly and truncate instead of overflowing.
                modifier = Modifier.weight(1f, fill = true),
            )
        }
    }
}

@Composable
private fun GamepadHintChip(
    hint: GamepadHint,
    scheme: ControllerGlyphScheme,
    faceStyle: ControllerGlyphFaceStyle,
    otherStyle: ControllerGlyphOtherStyle,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    val icons =
        remember(hint.button, scheme, faceStyle, otherStyle) {
            ControllerGlyphAssets.drawables(hint.button, scheme, faceStyle, otherStyle)
        }
    val boostLightNonFace = !darkTheme && !hint.button.isFaceGlyphButton()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icons.isNotEmpty()) {
            KenneyGlyphRow(
                icons = icons,
                lightModeNonFace = boostLightNonFace,
                otherStyle = otherStyle,
            )
        } else {
            Text(
                text = ControllerGlyphLabels.label(hint.button, scheme),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
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

@Composable
private fun KenneyGlyphRow(
    icons: List<DrawableResource>,
    lightModeNonFace: Boolean,
    otherStyle: ControllerGlyphOtherStyle,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icons.forEach { drawable ->
            KenneyGlyphImage(
                drawable = drawable,
                lightModeNonFace = lightModeNonFace,
                otherStyle = otherStyle,
            )
        }
    }
}

@Composable
private fun KenneyGlyphImage(
    drawable: DrawableResource,
    lightModeNonFace: Boolean,
    otherStyle: ControllerGlyphOtherStyle,
) {
    when {
        // Filled mono uses transparent letter cutouts — fill those holes with black on light UI.
        lightModeNonFace && otherStyle == ControllerGlyphOtherStyle.Filled -> {
            val source = imageResource(drawable)
            val filled =
                remember(drawable, source.width, source.height) {
                    source.withEnclosedTransparencyFilled(Color.Black)
                }
            Image(
                bitmap = filled,
                contentDescription = null,
                modifier = Modifier.size(GlyphSize),
                contentScale = ContentScale.Fit,
            )
        }

        // Outline mono is white ink — tint black so strokes/letters read on light backgrounds.
        lightModeNonFace && otherStyle == ControllerGlyphOtherStyle.Outline -> {
            Image(
                painter = painterResource(drawable),
                contentDescription = null,
                modifier = Modifier.size(GlyphSize),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(Color.Black, BlendMode.SrcIn),
            )
        }

        else -> {
            Image(
                painter = painterResource(drawable),
                contentDescription = null,
                modifier = Modifier.size(GlyphSize),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
