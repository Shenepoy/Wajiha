package com.wajiha.android.display

import android.app.Activity
import android.app.Presentation
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.WindowManager
import android.widget.ImageView
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags

/**
 * Frozen-frame cover on the secondary display via [Presentation] (same windowing
 * path as [SecondaryDisplayHost]'s live Overlay). TYPE_APPLICATION_OVERLAY is
 * unreliable on Thor display 4.
 *
 * Replaces without a gap: the new Presentation is shown before the old one is
 * dismissed. [hide] is ignored while a launch pin is active.
 */
internal class SecondaryTransitionCover {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var presentation: Presentation? = null
    private var bitmap: Bitmap? = null
    private var captureGeneration = 0L

    /** While [System.currentTimeMillis] is below this, [hide] is a no-op. */
    @Volatile
    var pinnedUntilElapsedRealtime: Long = 0L

    fun isPinned(): Boolean = android.os.SystemClock.elapsedRealtime() < pinnedUntilElapsedRealtime

    fun pinFor(durationMs: Long) {
        pinnedUntilElapsedRealtime =
            android.os.SystemClock.elapsedRealtime() + durationMs.coerceAtLeast(0L)
    }

    fun clearPin() {
        pinnedUntilElapsedRealtime = 0L
    }

    fun show(
        activity: Activity,
        onReady: (() -> Unit)? = null,
    ) {
        val display = activity.display
        val window = activity.window
        val decor = window?.decorView
        if (display == null || window == null || decor == null ||
            decor.width <= 0 || decor.height <= 0
        ) {
            onReady?.invoke()
            return
        }

        val captured =
            Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        val generation = ++captureGeneration

        fun done() {
            if (generation == captureGeneration) {
                onReady?.invoke()
            }
        }

        runCatching {
            PixelCopy.request(window, captured, { result ->
                if (generation != captureGeneration) {
                    captured.recycle()
                    return@request
                }
                if (result != PixelCopy.SUCCESS) {
                    captured.recycle()
                    WajihaLog.w(
                        WajihaTags.DISPLAY,
                        "secondaryCover: PixelCopy failed result=$result",
                    )
                    done()
                    return@request
                }
                present(activity, display, captured)
                done()
            }, mainHandler)
        }.onFailure {
            captured.recycle()
            WajihaLog.w(
                WajihaTags.DISPLAY,
                "secondaryCover: capture failed — ${it.message}",
            )
            done()
        }
    }

    private fun present(
        activity: Activity,
        display: android.view.Display,
        captured: Bitmap,
    ) {
        val view =
            ImageView(activity.createDisplayContext(display)).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                setImageBitmap(captured)
            }
        val next =
            Presentation(activity, display).apply {
                window?.apply {
                    setFormat(PixelFormat.OPAQUE)
                    setTitle("Wajiha secondary transition cover")
                    addFlags(
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    )
                }
                setContentView(view)
            }

        runCatching {
            next.show()
            next.window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
        }.onSuccess {
            val previous = presentation
            val previousBitmap = bitmap
            presentation = next
            bitmap = captured
            // Dismiss old only after the new Presentation is up — no black gap.
            if (previous != null) {
                runCatching { previous.dismiss() }
            }
            previousBitmap?.recycle()
            WajihaLog.d(WajihaTags.DISPLAY, "secondaryCover: shown (Presentation)")
        }.onFailure {
            captured.recycle()
            runCatching { next.dismiss() }
            WajihaLog.w(
                WajihaTags.DISPLAY,
                "secondaryCover: show failed — ${it.message}",
            )
        }
    }

    fun hide(force: Boolean = false) {
        if (!force && isPinned()) {
            WajihaLog.d(WajihaTags.DISPLAY, "secondaryCover: hide ignored — launch pin active")
            return
        }
        captureGeneration += 1
        clearPin()
        val current = presentation
        presentation = null
        val oldBitmap = bitmap
        bitmap = null
        if (current != null) {
            runCatching { current.dismiss() }
        }
        oldBitmap?.recycle()
    }
}
