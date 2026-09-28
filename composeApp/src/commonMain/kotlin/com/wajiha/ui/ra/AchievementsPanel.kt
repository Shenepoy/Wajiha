package com.wajiha.ui.ra

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import coil3.compose.AsyncImage
import com.wajiha.data.ra.RaMediaUrls
import com.wajiha.ui.components.SecondaryPanelScaffold
import com.wajiha.ui.components.WajihaEmptyState
import com.wajiha.ui.components.WajihaLoadingState
import com.wajiha.ui.components.gamepad.GamepadList
import com.wajiha.ui.components.gamepad.achievementsGamepadHints
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import org.koin.compose.koinInject

/**
 * Earned and locked badges for [gameId], or for the running session when
 * [gameId] is null.
 */
@Composable
fun AchievementsPanel(
    modifier: Modifier = Modifier,
    gameId: Long? = null,
    showGamepadHints: Boolean = true,
    embedInFolderPanel: Boolean = false,
) {
    val viewModel = koinInject<RaViewModel>()
    val state by viewModel.state.collectAsState()
    DisposableEffect(gameId) {
        if (gameId != null) viewModel.pinGame(gameId)
        onDispose {
            if (gameId != null) viewModel.pinGame(null)
        }
    }

    if (embedInFolderPanel) {
        AchievementsSectionContent(state = state, modifier = modifier)
    } else {
        SecondaryPanelScaffold(
            modifier = modifier,
            showGamepadHints = showGamepadHints,
            hints = achievementsGamepadHints,
        ) {
            AchievementsSectionContent(state = state)
        }
    }
}

@Composable
private fun AchievementsSectionContent(
    state: RaUiState,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        AchievementsBody(state)
    }
}

@Composable
private fun ColumnScope.AchievementsBody(state: RaUiState) {
    val progress = state.progress
    when {
        !state.configured -> {
            WajihaEmptyState(
                title = "RetroAchievements not configured",
                subtitle = "Add your RetroAchievements login in Settings → Scraper",
                modifier = Modifier.fillMaxSize(),
            )
        }

        state.loading && progress == null -> {
            WajihaLoadingState(modifier = Modifier.fillMaxSize())
        }

        progress == null -> {
            WajihaEmptyState(
                title = "No achievement data",
                subtitle = state.message,
                modifier = Modifier.fillMaxSize(),
            )
        }

        else -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = WajihaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm + WajihaSpacing.xs),
            ) {
                progress.imageIcon?.let { icon ->
                    AsyncImage(
                        model = RaMediaUrls.media(icon),
                        contentDescription = progress.title,
                        modifier = Modifier.size(WajihaSpacing.touchMin).clip(WajihaShapes.focus),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = progress.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "${progress.numAwardedToUser} / ${progress.numAchievements} achievements",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LinearProgressIndicator(
                progress = {
                    if (progress.numAchievements == 0) {
                        0f
                    } else {
                        progress.numAwardedToUser.toFloat() / progress.numAchievements
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = WajihaSpacing.sm),
            )
            GamepadList(
                items = progress.sortedAchievements,
                key = { it.id },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { achievement ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm + WajihaSpacing.xs),
                ) {
                    AsyncImage(
                        model =
                            if (achievement.earned) {
                                achievement.badgeUrl
                            } else {
                                achievement.badgeLockedUrl
                            },
                        contentDescription = achievement.title,
                        modifier = Modifier.size(WajihaSpacing.touchMin - WajihaSpacing.xs).clip(WajihaShapes.focus),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = achievement.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color =
                                if (achievement.earned) {
                                    MaterialTheme.colorScheme.onBackground
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                        )
                        Text(
                            text = achievement.description,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "${achievement.points}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
