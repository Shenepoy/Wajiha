package com.wajiha.ui.components.gamepad

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wajiha.data.prefs.AppSettings
import com.wajiha.data.prefs.SettingsRepository
import com.wajiha.input.ControllerGlyphAssets
import com.wajiha.input.ControllerGlyphFaceStyle
import com.wajiha.input.ControllerGlyphLabels
import com.wajiha.input.ControllerGlyphOtherStyle
import com.wajiha.input.ControllerGlyphScheme
import com.wajiha.input.ControllerGlyphStore
import com.wajiha.input.GamepadHintButton
import com.wajiha.input.isFaceGlyphButton
import com.wajiha.input.withEnclosedTransparencyFilled
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.imageResource
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

/**
 * Kenney (or text) glyph for a single [GamepadHintButton], matching action-bar styling.
 */
@Composable
fun GamepadHintGlyph(
    button: GamepadHintButton,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    glyphStore: ControllerGlyphStore = koinInject(),
    settingsRepository: SettingsRepository = koinInject(),
) {
    val settings by settingsRepository.settings.collectAsState(initial = AppSettings())
    val devices by glyphStore.devices.collectAsState()
    val lastInputType by glyphStore.lastInputType.collectAsState()
    val showGlyphs =
        remember(settings.controllerGlyphScheme, devices.size) {
            ControllerGlyphLabels.shouldShowActionBarHints(
                schemePref = settings.controllerGlyphScheme,
                connectedDeviceCount = devices.size,
            )
        }
    if (!showGlyphs) return

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
    val icons =
        remember(button, scheme, faceStyle, otherStyle) {
            ControllerGlyphAssets.drawables(button, scheme, faceStyle, otherStyle)
        }
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val boostLightNonFace = !darkTheme && !button.isFaceGlyphButton()

    if (icons.isNotEmpty()) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icons.forEach { drawable ->
                KenneyGlyphImage(
                    drawable = drawable,
                    size = size,
                    lightModeNonFace = boostLightNonFace,
                    otherStyle = otherStyle,
                )
            }
        }
    } else {
        Text(
            text = ControllerGlyphLabels.label(button, scheme),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
    }
}

@Composable
internal fun KenneyGlyphImage(
    drawable: DrawableResource,
    size: Dp,
    lightModeNonFace: Boolean,
    otherStyle: ControllerGlyphOtherStyle,
) {
    when {
        lightModeNonFace && otherStyle == ControllerGlyphOtherStyle.Filled -> {
            val source = imageResource(drawable)
            val filled =
                remember(drawable, source.width, source.height) {
                    source.withEnclosedTransparencyFilled(Color.Black)
                }
            Image(
                bitmap = filled,
                contentDescription = null,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Fit,
            )
        }

        lightModeNonFace && otherStyle == ControllerGlyphOtherStyle.Outline -> {
            Image(
                painter = painterResource(drawable),
                contentDescription = null,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(Color.Black, BlendMode.SrcIn),
            )
        }

        else -> {
            Image(
                painter = painterResource(drawable),
                contentDescription = null,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
