package com.wajiha.android.display

import android.app.Presentation
import android.graphics.PixelFormat
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.wajiha.android.input.GamepadGate
import com.wajiha.android.input.GamepadKeyRouter
import com.wajiha.android.input.TriggerAxisHandler
import com.wajiha.android.input.handleGamepadKey
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import com.wajiha.state.GamepadOwner
import com.wajiha.ui.secondary.SecondaryApp
import kotlinx.coroutines.flow.StateFlow
import java.lang.ref.WeakReference

/**
 * Owns the one live secondary Compose tree. The Activity remains the Android
 * SECONDARY_HOME/task anchor; the overlay becomes the renderer only when a
 * foreign Presentation remains above that task.
 */
class SecondaryDisplayHost {
    private val ownership = SecondaryRenderOwnership()
    val surface: StateFlow<SecondaryRenderSurface> = ownership.surface

    private var activityRef: WeakReference<ComponentActivity>? = null
    private var gamepadKeyRouter: GamepadKeyRouter? = null
    private var triggerAxisHandler: TriggerAxisHandler? = null
    private var gamepadGate: GamepadGate? = null
    private var overlayPresentation: Presentation? = null
    private var overlayView: SecondaryOverlayContainer? = null

    fun attach(
        activity: ComponentActivity,
        gamepadKeyRouter: GamepadKeyRouter,
        triggerAxisHandler: TriggerAxisHandler,
        gamepadGate: GamepadGate,
    ) {
        activityRef = WeakReference(activity)
        this.gamepadKeyRouter = gamepadKeyRouter
        this.triggerAxisHandler = triggerAxisHandler
        this.gamepadGate = gamepadGate
    }

    fun detach(activity: ComponentActivity) {
        if (activityRef?.get() === activity) {
            activityRef = null
            hideOverlay()
            gamepadKeyRouter = null
            triggerAxisHandler = null
            gamepadGate = null
        }
    }

    fun showOverlay(activity: ComponentActivity) {
        val router = gamepadKeyRouter
        val triggers = triggerAxisHandler
        val gate = gamepadGate
        if (router == null || triggers == null || gate == null) {
            returnToActivity("input dependencies unavailable")
            return
        }
        if (surface.value == SecondaryRenderSurface.Overlay && overlayView != null) return
        hideOverlayWindow()
        ownership.claim(SecondaryRenderSurface.Overlay)

        val display = activity.display
        if (display == null) {
            returnToActivity("missing display")
            return
        }
        val view =
            SecondaryOverlayContainer(
                context = activity.createDisplayContext(display),
                activity = activity,
                gamepadKeyRouter = router,
                triggerAxisHandler = triggers,
                gamepadGate = gate,
            ).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewTreeOnBackPressedDispatcherOwner(activity)
                setContent()
            }
        val presentation =
            Presentation(activity, display).apply {
                window?.apply {
                    setFormat(PixelFormat.OPAQUE)
                    setTitle("Wajiha live secondary")
                    addFlags(
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    )
                }
                setContentView(view)
            }

        runCatching {
            presentation.show()
            presentation.window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
        }.onSuccess {
            overlayPresentation = presentation
            overlayView = view
            router.attachSecondaryOverlay(view::dispatchFromRouter)
            view.isFocusableInTouchMode = true
            view.requestFocus()
            WajihaLog.i(
                WajihaTags.DISPLAY,
                "secondaryHost: Activity → live Presentation",
            )
        }.onFailure {
            WajihaLog.w(
                WajihaTags.DISPLAY,
                "secondaryHost: overlay show failed — ${it.message}",
            )
            returnToActivity("overlay show failed")
        }
    }

    fun showActivity(reason: String) {
        if (surface.value == SecondaryRenderSurface.Activity && overlayView == null) return
        ownership.claim(SecondaryRenderSurface.Activity)
        activityRef?.get()?.window?.decorView?.postOnAnimation {
            hideOverlayWindow()
            WajihaLog.i(WajihaTags.DISPLAY, "secondaryHost: Overlay → Activity reason=$reason")
        } ?: hideOverlayWindow()
    }

    fun hideOverlay() {
        ownership.claim(SecondaryRenderSurface.Activity)
        hideOverlayWindow()
    }

    private fun returnToActivity(reason: String) {
        ownership.claim(SecondaryRenderSurface.Activity)
        hideOverlayWindow()
        WajihaLog.d(WajihaTags.DISPLAY, "secondaryHost: use Activity reason=$reason")
    }

    private fun hideOverlayWindow() {
        gamepadKeyRouter?.detachSecondaryOverlay()
        val presentation = overlayPresentation
        overlayPresentation = null
        overlayView = null
        if (presentation != null) {
            runCatching { presentation.dismiss() }
        }
    }
}

private class SecondaryOverlayContainer(
    context: android.content.Context,
    private val activity: ComponentActivity,
    private val gamepadKeyRouter: GamepadKeyRouter,
    private val triggerAxisHandler: TriggerAxisHandler,
    private val gamepadGate: GamepadGate,
) : FrameLayout(context) {
    private val composeView =
        ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        }

    init {
        addView(
            composeView,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    fun setContent() {
        composeView.setContent { SecondaryApp() }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = dispatchFromRouter(event)

    fun dispatchFromRouter(event: KeyEvent): Boolean {
        if (gamepadGate.shouldBlockGamepad()) return false
        return gamepadKeyRouter.dispatch(GamepadOwner.Secondary, event) { remappedOrRaw ->
            handleGamepadKey(activity, remappedOrRaw) {
                super.dispatchKeyEvent(remappedOrRaw)
            } || super.dispatchKeyEvent(remappedOrRaw)
        }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (!gamepadGate.shouldBlockGamepad() && triggerAxisHandler.onGenericMotion(event)) {
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }
}
