package com.wajiha.android.display

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags

/**
 * Keeps the last Wajiha frame visible while Thor briefly inserts its stock
 * secondary launcher during a bottom-screen HOME transition.
 */
internal class SecondaryTransitionCover {
    private var windowManager: WindowManager? = null
    private var imageView: ImageView? = null
    private var bitmap: Bitmap? = null

    fun show(activity: Activity) {
        hide()
        val display = activity.display ?: return
        val decor = activity.window.decorView
        if (decor.width <= 0 || decor.height <= 0) return

        val captured =
            runCatching {
                Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888).also {
                    decor.draw(Canvas(it))
                }
            }.getOrElse {
                WajihaLog.w(
                    WajihaTags.DISPLAY,
                    "secondaryCover: capture failed — ${it.message}",
                )
                return
            }
        val displayContext = activity.createDisplayContext(display)
        val manager = displayContext.getSystemService(WindowManager::class.java)
        val image =
            ImageView(displayContext).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                setImageBitmap(captured)
            }
        val params =
            WindowManager
                .LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.OPAQUE,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    title = "Wajiha secondary transition cover"
                }

        runCatching { manager.addView(image, params) }
            .onSuccess {
                windowManager = manager
                imageView = image
                bitmap = captured
            }.onFailure {
                captured.recycle()
                WajihaLog.w(
                    WajihaTags.DISPLAY,
                    "secondaryCover: show failed — ${it.message}",
                )
            }
    }

    fun hide() {
        val manager = windowManager
        val image = imageView
        windowManager = null
        imageView = null
        val oldBitmap = bitmap
        bitmap = null
        if (manager != null && image != null) {
            runCatching { manager.removeViewImmediate(image) }
        }
        oldBitmap?.recycle()
    }
}
