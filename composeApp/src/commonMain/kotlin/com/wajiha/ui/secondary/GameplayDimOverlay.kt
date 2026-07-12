package com.wajiha.ui.secondary

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.wajiha.input.GamepadKeys
import com.wajiha.state.DualScreenState
import com.wajiha.state.DualScreenStore
import com.wajiha.state.NowPlayingState
import com.wajiha.state.SecondaryMode
import kotlinx.coroutines.delay

private const val GAMEPLAY_DIM_FADE_MS = 400

internal fun shouldDimGameplay(
    nowPlaying: NowPlayingState?,
    mode: SecondaryMode,
    dualState: DualScreenState,
    gameDimEnabled: Boolean,
    gameDimOnlyOnNowPlaying: Boolean
): Boolean = nowPlaying != null &&
    mode != SecondaryMode.Off &&
    dualState == DualScreenState.GameRunning &&
    gameDimEnabled &&
    (!gameDimOnlyOnNowPlaying || mode == SecondaryMode.NowPlaying)

/**
 * Semi-transparent black scrim over secondary content while a game runs on the
 * top display. Separate from [SecondaryMode.Off] blackout — dim keeps the
 * underlying mode visible (or recoverable) under the overlay.
 *
 * Lifts while the user interacts with the bottom screen; fades back after
 * [DualScreenStore.gameplayDimTimeoutSeconds] of idle time.
 */
@Composable
internal fun rememberGameplayDimAlpha(
    store: DualScreenStore,
    liftedByInteraction: Boolean
): Float {
    val nowPlaying by store.nowPlayingUiState.collectAsState()
    val mode by store.secondaryMode.collectAsState()
    val dualState by store.state.collectAsState()

    val shouldDimEventually = shouldDimGameplay(
        nowPlaying = nowPlaying,
        mode = mode,
        dualState = dualState,
        gameDimEnabled = store.gameDimEnabled,
        gameDimOnlyOnNowPlaying = store.gameDimOnlyOnNowPlaying
    )

    var delayElapsed by remember { mutableStateOf(false) }

    LaunchedEffect(
        shouldDimEventually,
        store.gameplayDimTimeoutSeconds,
        nowPlaying?.packageName
    ) {
        if (!shouldDimEventually) {
            delayElapsed = false
            return@LaunchedEffect
        }
        val timeoutSeconds = store.gameplayDimTimeoutSeconds
        if (timeoutSeconds <= 0) {
            delayElapsed = true
            return@LaunchedEffect
        }
        delayElapsed = false
        delay(timeoutSeconds * 1000L)
        delayElapsed = true
    }

    val targetAlpha = when {
        !shouldDimEventually -> 0f
        liftedByInteraction -> 0f
        !delayElapsed -> 0f
        else -> store.gameDimPercent.coerceIn(0, 100) / 100f
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = GAMEPLAY_DIM_FADE_MS),
        label = "gameplayDimAlpha"
    )

    return animatedAlpha
}

@Composable
fun GameplayDimScrim(
    alpha: Float,
    modifier: Modifier = Modifier
) {
    if (alpha <= 0.001f) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = alpha))
    )
}

/**
 * Host for any secondary route: paints [content], gameplay dim scrim, then [foreground].
 * [foreground] receives whether the dim scrim is visible so overlays (e.g. Now Playing
 * chip) can hide while gameplay dim is active.
 *
 * Touch and gamepad activity on the bottom screen temporarily lifts the dim scrim.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SecondarySurface(
    store: DualScreenStore,
    modifier: Modifier = Modifier,
    foreground: (@Composable BoxScope.(gameplayDimScrimVisible: Boolean) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val nowPlaying by store.nowPlayingUiState.collectAsState()
    val mode by store.secondaryMode.collectAsState()
    val dualState by store.state.collectAsState()

    val shouldDimEventually = shouldDimGameplay(
        nowPlaying = nowPlaying,
        mode = mode,
        dualState = dualState,
        gameDimEnabled = store.gameDimEnabled,
        gameDimOnlyOnNowPlaying = store.gameDimOnlyOnNowPlaying
    )

    var dimLiftedByInteraction by remember { mutableStateOf(false) }
    var interactionGeneration by remember { mutableIntStateOf(0) }

    LaunchedEffect(shouldDimEventually) {
        if (!shouldDimEventually) {
            dimLiftedByInteraction = false
        }
    }

    LaunchedEffect(interactionGeneration, shouldDimEventually, store.gameplayDimTimeoutSeconds) {
        if (!dimLiftedByInteraction || !shouldDimEventually) return@LaunchedEffect
        val timeoutSeconds = store.gameplayDimTimeoutSeconds
        if (timeoutSeconds <= 0) return@LaunchedEffect
        delay(timeoutSeconds * 1000L)
        dimLiftedByInteraction = false
    }

    val onInteraction = {
        if (shouldDimEventually) {
            dimLiftedByInteraction = true
            interactionGeneration++
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(shouldDimEventually) {
                if (!shouldDimEventually) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onInteraction()
                }
            }
            .onPreviewKeyEvent { event ->
                if (
                    shouldDimEventually &&
                    event.type == KeyEventType.KeyDown &&
                    GamepadKeys.switchesToGamepadMode(event)
                ) {
                    onInteraction()
                }
                false
            }
    ) {
        val gameplayDimAlpha = rememberGameplayDimAlpha(
            store = store,
            liftedByInteraction = dimLiftedByInteraction
        )
        val gameplayDimScrimVisible = gameplayDimAlpha > 0.001f

        content()
        GameplayDimScrim(alpha = gameplayDimAlpha)
        foreground?.invoke(this, gameplayDimScrimVisible)
    }
}
