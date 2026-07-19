package com.wajiha.ui.secondary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.wajiha.input.GamepadKeys
import com.wajiha.state.NowPlayingState
import com.wajiha.ui.components.gamepad.GamepadTile
import com.wajiha.ui.theme.WajihaColors
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing

private val SessionOnTopGreen = Color(0xFF4CAF50)

/** Session tile for the unified game grid — same [GamepadTile] shell and 3:4 aspect as game tiles. */
@Composable
fun SessionGridTile(
    session: NowPlayingState,
    isOnTop: Boolean,
    isFeatured: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    gamepadFocusable: Boolean = true,
    navHighlighted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val displayName = sessionDisplayLabel(session) ?: session.packageName
    val elapsedMs = rememberSessionElapsedMs(session)
    val statusLabel = formatSessionElapsed(elapsedMs)

    GamepadTile(
        selected = selected,
        onSelect = onSelect,
        onLaunch = onOpen,
        onLongPress = onLongPress,
        touchSwitchMode = true,
        focusRequester = focusRequester,
        focusId = "session:${session.packageName}",
        gamepadFocusable = gamepadFocusable,
        navHighlighted = navHighlighted || (isFeatured && selected),
        modifier =
            modifier
                .aspectRatio(3f / 4f)
                .focusProperties {
                    left = FocusRequester.Cancel
                }.onPreviewKeyEvent { event ->
                    when {
                        GamepadKeys.isY(event.type, event.key) -> {
                            onClose()
                            true
                        }

                        GamepadKeys.isConfirm(event.type, event.key) -> {
                            onOpen()
                            true
                        }

                        else -> {
                            false
                        }
                    }
                },
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = scheme.surfaceVariant,
            shape = WajihaShapes.tile,
        ) {
            Box {
                if (session.boxartPath != null) {
                    AsyncImage(
                        model = session.boxartPath,
                        contentDescription = displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (!isOnTop) {
                        Box(
                            modifier =
                                Modifier
                                    .matchParentSize()
                                    .background(Color.Black.copy(alpha = 0.45f)),
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, WajihaColors.TileScrim),
                                    ),
                                ),
                    ) {
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = WajihaColors.OnDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier.padding(
                                    horizontal = WajihaSpacing.sm,
                                    vertical = WajihaSpacing.sm,
                                ),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(WajihaSpacing.sm),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            color = scheme.onSurfaceVariant,
                        )
                        Text(
                            text = statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center,
                            color = scheme.onSurfaceVariant.copy(alpha = 0.85f),
                            modifier = Modifier.padding(top = WajihaSpacing.xs),
                        )
                    }
                }
                if (isOnTop) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(WajihaSpacing.sm)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(SessionOnTopGreen),
                    )
                }
            }
        }
    }
}
