package com.wajiha.ui.secondary

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.wajiha.platform.AppActions
import com.wajiha.state.DualScreenStore
import com.wajiha.state.NowPlayingState
import com.wajiha.state.sessionDisplayLabel as stateSessionDisplayLabel

/** UI-facing alias for [com.wajiha.state.sessionDisplayLabel]. */
fun sessionDisplayLabel(state: NowPlayingState?): String? = stateSessionDisplayLabel(state)

@Composable
fun rememberOpenSession(
    store: DualScreenStore,
    appActions: AppActions,
    topDisplayPackage: String?,
): (String) -> Unit =
    remember(store, appActions, topDisplayPackage) {
        { pkg ->
            openSessionSurface(store, appActions, topDisplayPackage, pkg)
        }
    }

/** Pill / session tile: focus last launch display if bg, then open Now Running. */
fun openSessionSurface(
    store: DualScreenStore,
    appActions: AppActions,
    topDisplayPackage: String?,
    packageName: String,
) {
    if (topDisplayPackage != packageName) {
        appActions.focusApp(packageName)
    } else {
        store.switchToSession(packageName)
    }
    store.requestNavigateToNowPlaying()
}
