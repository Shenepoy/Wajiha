package com.wajiha.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.wajiha.platform.SystemControls
import com.wajiha.state.DualScreenStore
import com.wajiha.ui.gamedetail.GameDetailScreen
import com.wajiha.ui.home.GameTile
import com.wajiha.ui.home.HomeUiState
import com.wajiha.ui.home.HomeViewModel
import com.wajiha.ui.scraper.ScraperScreen
import com.wajiha.ui.scraper.ScraperViewModel
import com.wajiha.ui.settings.PlatformPickerScreen
import com.wajiha.ui.settings.PlatformSettingsScreen
import com.wajiha.ui.settings.SettingsScreen
import com.wajiha.ui.settings.SettingsViewModel
import org.koin.compose.koinInject

data class LauncherHero(
    val focusedTile: GameTile?,
    val platformName: String?,
)

fun launcherHero(
    state: HomeUiState,
    focusedGameId: Long?,
): LauncherHero {
    val focusedTile =
        state.tiles.firstOrNull { it.game.id == focusedGameId }
            ?: state.recent.firstOrNull()
    val platformName =
        focusedTile?.let { tile ->
            state.platforms.firstOrNull { it.id == tile.game.platformId }?.name
        }
    return LauncherHero(focusedTile, platformName)
}

@Composable
fun LauncherGameDetailRoute(
    gameId: Long?,
    store: DualScreenStore,
    viewModel: HomeViewModel,
    dualDisplay: Boolean,
    secondaryDisplayId: Int?,
    onBack: () -> Unit,
    onMissingGame: () -> Unit,
) {
    if (gameId == null) {
        LaunchedEffect(Unit) { onMissingGame() }
        return
    }
    GameDetailScreen(
        gameId = gameId,
        secondaryDisplayId = secondaryDisplayId,
        dualDisplay = dualDisplay,
        onBack = onBack,
    )
    LaunchedEffect(gameId) {
        store.setGameDetailGameId(gameId)
    }
}

@Composable
fun LauncherSettingsFlow(
    route: LauncherSettingsRoute,
    settingsViewModel: SettingsViewModel,
    viewModel: HomeViewModel,
    store: DualScreenStore,
    platformDetailId: String?,
    onPlatformDetailIdChange: (String?) -> Unit,
    onRouteChange: (LauncherSettingsRoute) -> Unit,
    onExitSettings: () -> Unit,
    content: @Composable () -> Unit = {},
) {
    when (route) {
        LauncherSettingsRoute.Hidden -> {
            content()
        }

        LauncherSettingsRoute.Settings -> {
            SettingsScreen(
                settingsViewModel = settingsViewModel,
                onBack = onExitSettings,
                onAddPlatform = {
                    viewModel.playOpen()
                    onRouteChange(LauncherSettingsRoute.PlatformPicker)
                },
                onOpenPlatform = { id ->
                    onPlatformDetailIdChange(id)
                    viewModel.playOpen()
                    onRouteChange(LauncherSettingsRoute.PlatformDetail)
                },
                onSectionChange = store::setSettingsSectionLabel,
            )
        }

        LauncherSettingsRoute.PlatformPicker -> {
            PlatformPickerScreen(
                settingsViewModel = settingsViewModel,
                onBack = {
                    viewModel.playBack()
                    onRouteChange(LauncherSettingsRoute.Settings)
                },
                onPick = { id ->
                    onPlatformDetailIdChange(id)
                    viewModel.playOpen()
                    onRouteChange(LauncherSettingsRoute.PlatformDetail)
                },
            )
            LaunchedEffect(Unit) {
                store.setSettingsSectionLabel("Library")
            }
        }

        LauncherSettingsRoute.PlatformDetail -> {
            val id = platformDetailId
            if (id == null) {
                LaunchedEffect(Unit) { onRouteChange(LauncherSettingsRoute.Settings) }
            } else {
                PlatformSettingsScreen(
                    platformId = id,
                    onBack = {
                        viewModel.playBack()
                        onRouteChange(LauncherSettingsRoute.Settings)
                    },
                )
                LaunchedEffect(Unit) {
                    store.setSettingsSectionLabel("Library")
                }
            }
        }

        LauncherSettingsRoute.Scraper -> {
            ScraperScreen(
                viewModel = koinInject<ScraperViewModel>(),
                onBack = {
                    viewModel.playBack()
                    onRouteChange(
                        if (platformDetailId != null) {
                            LauncherSettingsRoute.PlatformDetail
                        } else {
                            LauncherSettingsRoute.Settings
                        },
                    )
                },
            )
            LaunchedEffect(Unit) {
                store.setSettingsSectionLabel("Scraper")
            }
        }
    }
}

enum class LauncherSettingsRoute {
    Hidden,
    Settings,
    PlatformPicker,
    PlatformDetail,
    Scraper,
}

@Composable
fun SyncSystemHeroSnapshot(
    systemControls: SystemControls,
    store: DualScreenStore,
) {
    LaunchedEffect(Unit) {
        systemControls.refreshStatus()
        val status = systemControls.status.value
        store.setSystemHeroSnapshot(
            batteryPercent = status.batteryPercent,
            charging = status.charging,
            wifiEnabled = status.wifiEnabled,
        )
    }
}
