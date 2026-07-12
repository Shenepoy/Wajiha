package com.wajiha.android.util

import android.app.ActivityManager
import android.content.Context
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags

/**
 * Force-stops a package. [ActivityManager.killBackgroundProcesses] misses
 * foreground emulators; reflection / `am force-stop` matches Y-close behavior.
 */
object PackageKiller {

    fun forceStopPackageBestEffort(context: Context, packageName: String): Boolean {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val method = ActivityManager::class.java.getMethod(
                "forceStopPackage",
                String::class.java
            )
            method.invoke(am, packageName)
            WajihaLog.i(WajihaTags.NOW_PLAYING, "forceStopPackage: ok $packageName")
            return true
        } catch (e: Exception) {
            val cause = (e as? java.lang.reflect.InvocationTargetException)?.cause ?: e
            WajihaLog.w(
                WajihaTags.NOW_PLAYING,
                "forceStopPackage: reflect failed $packageName — ${cause.message}"
            )
        }
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("am", "force-stop", packageName))
            val exit = process.waitFor()
            val ok = exit == 0
            if (ok) {
                WajihaLog.i(
                    WajihaTags.NOW_PLAYING,
                    "forceStopPackage: am force-stop $packageName exit=$exit"
                )
            } else {
                WajihaLog.w(
                    WajihaTags.NOW_PLAYING,
                    "forceStopPackage: am force-stop $packageName exit=$exit " +
                        "(needs FORCE_STOP_PACKAGES / priv-app on stock Android)"
                )
            }
            ok
        } catch (e: Exception) {
            WajihaLog.w(
                WajihaTags.NOW_PLAYING,
                "forceStopPackage: unavailable for $packageName — ${e.message}"
            )
            false
        }
    }
}
