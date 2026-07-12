package com.wajiha.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.wajiha.input.GamepadScreen
import com.wajiha.input.requestContentFocus
import com.wajiha.state.GamepadOwner
import com.wajiha.state.HeroContext
import com.wajiha.ui.home.HomeUiState
import com.wajiha.ui.home.TopScreen

/**
 * Dual-display layout roles for the SELECT / swap-screen toggle.
 *
 * - Menu display: grid and launcher routes (settings, apps, scraper, …).
 * - Hero display: contextual [TopScreen] artwork driven by [HeroContext].
 *
 * When roles are swapped, the menu moves to the primary (top) activity and the
 * hero moves to the secondary (bottom) activity — on every route, not only Home.
 */
fun menuOnPrimary(
    isDual: Boolean,
    swapScreenRoles: Boolean,
): Boolean = !isDual || swapScreenRoles

fun heroOnPrimary(
    isDual: Boolean,
    swapScreenRoles: Boolean,
): Boolean = isDual && !swapScreenRoles

fun menuOnSecondary(
    isDual: Boolean,
    swapScreenRoles: Boolean,
): Boolean = isDual && !swapScreenRoles

fun heroOnSecondary(
    isDual: Boolean,
    swapScreenRoles: Boolean,
): Boolean = isDual && swapScreenRoles

@Composable
fun LauncherHeroPane(
    state: HomeUiState,
    focusedGameId: Long?,
    heroContext: HeroContext,
    modifier: Modifier = Modifier,
    gamepadOwner: GamepadOwner? = null,
    onClaimGamepad: ((GamepadOwner) -> Unit)? = null,
) {
    val hero = launcherHero(state, focusedGameId)
    val contentFocus = remember { FocusRequester() }
    GamepadScreen(
        layerId = "launcher_hero",
        modifier = modifier,
        owner = gamepadOwner,
        onClaimGamepad = onClaimGamepad,
        onOwnerGainedFocus = { contentFocus.requestContentFocus() },
    ) {
        TopScreen(
            focused = hero.focusedTile,
            platformName = hero.platformName,
            heroContext = heroContext,
            contentFocusRequester = contentFocus,
            modifier = Modifier,
        )
    }
}
