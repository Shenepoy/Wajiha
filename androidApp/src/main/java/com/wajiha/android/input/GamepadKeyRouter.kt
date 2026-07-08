package com.wajiha.android.input

import android.os.Looper
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import com.wajiha.data.prefs.SettingsRepository
import com.wajiha.platform.AppActions
import com.wajiha.platform.UiSound
import com.wajiha.state.DualScreenState
import com.wajiha.state.DualScreenStore
import com.wajiha.state.GamepadOwner
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import java.lang.ref.WeakReference
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Same-process gamepad routing between [com.wajiha.android.MainActivity] and
 * [com.wajiha.android.SecondaryHomeActivity].
 *
 * Android delivers keys to the focused display's window. While dual-browsing,
 * the games menu is usually on the bottom screen, but a touch on the top
 * display often leaves system focus there — so D-pad / A would miss the grid.
 * This router tracks which activity owns gamepad input via [DualScreenStore]
 * and re-dispatches events onto that activity when they arrive on the other.
 *
 * Device limit: forwarding injects into the other activity's view hierarchy
 * without moving window focus, so touch targets on the focused display stay
 * valid. Hardware key focus APIs that require the window to be focused may
 * still no-op on some OEM builds.
 */
class GamepadKeyRouter(
    private val store: DualScreenStore,
    private val settingsRepository: SettingsRepository,
    private val appActions: AppActions,
    private val scope: CoroutineScope
) {
    private var primaryRef: WeakReference<ComponentActivity>? = null
    private var secondaryRef: WeakReference<ComponentActivity>? = null

    fun attach(owner: GamepadOwner, activity: ComponentActivity) {
        when (owner) {
            GamepadOwner.Primary -> primaryRef = WeakReference(activity)
            GamepadOwner.Secondary -> secondaryRef = WeakReference(activity)
        }
    }

    fun detach(owner: GamepadOwner, activity: ComponentActivity) {
        when (owner) {
            GamepadOwner.Primary -> {
                if (primaryRef?.get() === activity) primaryRef = null
            }
            GamepadOwner.Secondary -> {
                if (secondaryRef?.get() === activity) secondaryRef = null
            }
        }
    }

    /**
     * @param from which activity received the system key event
     * @param localDispatch run mapper + super dispatch on [from]
     * @return true if consumed
     */
    fun dispatch(
        from: GamepadOwner,
        event: KeyEvent,
        localDispatch: (KeyEvent) -> Boolean
    ): Boolean {
        if (isSwapScreenKey(event.keyCode) && event.action == KeyEvent.ACTION_UP) {
            scope.launch {
                val swapped = settingsRepository.toggleSwapScreenRoles()
                WajihaLog.i(
                    WajihaTags.GAMEPAD,
                    "map: SELECT → swapScreenRoles=$swapped (from=$from)"
                )
                appActions.playSound(UiSound.Navigate)
            }
            return true
        }
        val owner = effectiveOwner()
        if (from == owner) {
            return localDispatch(event)
        }
        val target = activityFor(owner)
        if (target == null || target.isFinishing || target.isDestroyed) {
            return localDispatch(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN && isMappedGamepadKey(event.keyCode)) {
            WajihaLog.d(
                WajihaTags.GAMEPAD,
                "forward: keyCode=${event.keyCode} from=$from → owner=$owner " +
                    "target=${target.javaClass.simpleName}"
            )
        }
        return runOnMainBlocking {
            handleGamepadKey(target, event) { remapped ->
                target.dispatchKeyEvent(remapped)
            } || target.dispatchKeyEvent(event)
        }
    }

    private fun effectiveOwner(): GamepadOwner {
        if (store.state.value == DualScreenState.SingleDisplay) {
            return GamepadOwner.Primary
        }
        return store.gamepadOwner.value
    }

    private fun activityFor(owner: GamepadOwner): ComponentActivity? =
        when (owner) {
            GamepadOwner.Primary -> primaryRef?.get()
            GamepadOwner.Secondary -> secondaryRef?.get()
        }
}

private fun isSwapScreenKey(keyCode: Int): Boolean =
    keyCode == KeyEvent.KEYCODE_BUTTON_SELECT ||
        keyCode == KeyEvent.KEYCODE_BUTTON_MODE

private fun isMappedGamepadKey(keyCode: Int): Boolean =
    keyCode == KeyEvent.KEYCODE_BUTTON_A ||
        keyCode == KeyEvent.KEYCODE_BUTTON_B ||
        isSwapScreenKey(keyCode)

private fun <T> runOnMainBlocking(block: () -> T): T {
    if (Looper.getMainLooper().thread === Thread.currentThread()) return block()
    val result = AtomicReference<T>()
    val latch = CountDownLatch(1)
    android.os.Handler(Looper.getMainLooper()).post {
        result.set(block())
        latch.countDown()
    }
    latch.await()
    return result.get()
}
