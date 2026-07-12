package com.wajiha.android.monitor

import android.app.ActivityManager
import android.content.Context
import android.view.Display

/**
 * Resolves the foreground package on the top (default) display.
 *
 * Thor dual-screen: display 0 is the game panel; display 4 is the touch grid.
 * [android.app.ActivityManager.getRunningTasks] is restricted on retail builds,
 * so we prefer the hidden [android.app.ActivityTaskManager.getService].getTasks
 * API with an explicit display id (matches `dumpsys activity activities` Display #0).
 */
internal object TopDisplayForegroundResolver {
    fun foregroundPackage(context: Context): String? = queryViaActivityTaskManager() ?: queryViaRunningTasks(context)

    private fun queryViaActivityTaskManager(): String? =
        try {
            val atmClass = Class.forName("android.app.ActivityTaskManager")
            val service = atmClass.getMethod("getService").invoke(null)
            val getTasks =
                service.javaClass.getMethod(
                    "getTasks",
                    Int::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                )

            @Suppress("UNCHECKED_CAST")
            val tasks =
                getTasks.invoke(
                    service,
                    8,
                    false,
                    false,
                    Display.DEFAULT_DISPLAY,
                ) as? List<ActivityManager.RunningTaskInfo>
            tasks
                ?.firstOrNull { task ->
                    task.topActivity != null
                }?.topActivity
                ?.packageName
        } catch (_: Exception) {
            null
        }

    @Suppress("DEPRECATION")
    private fun queryViaRunningTasks(context: Context): String? =
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am
                .getRunningTasks(12)
                .firstOrNull { task -> task.topActivity != null }
                ?.topActivity
                ?.packageName
        } catch (_: Exception) {
            null
        }
}
