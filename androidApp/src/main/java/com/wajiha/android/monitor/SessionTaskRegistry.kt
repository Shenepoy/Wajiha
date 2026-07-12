package com.wajiha.android.monitor

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags

/**
 * Caches emulator task IDs at launch time — [ActivityManager.getRunningTasks] from a
 * normal app cannot see other packages' background tasks on Thor, but moveTaskToFront
 * still works with a known taskId from the launch window.
 */
internal object SessionTaskRegistry {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val taskIds = mutableMapOf<String, Int>()
    private val components = mutableMapOf<String, ComponentName>()

    fun recordLaunch(
        context: Context,
        packageName: String,
        activityName: String?,
        delayMs: Long = 400L
    ) {
        if (activityName != null) {
            components[packageName] = ComponentName(packageName, activityName)
        }
        mainHandler.postDelayed({ captureTaskId(context, packageName) }, delayMs)
        mainHandler.postDelayed({ captureTaskId(context, packageName) }, delayMs + 1_500L)
    }

    fun captureTaskId(context: Context, packageName: String) {
        TopDisplayTaskResolver.taskIdForPackage(context, packageName)?.let { id ->
            taskIds[packageName] = id
            WajihaLog.d(WajihaTags.DISPLAY, "SessionTaskRegistry: taskId=$id pkg=$packageName")
        }
    }

    fun hasTask(packageName: String): Boolean = packageName in taskIds

    fun clear(packageName: String) {
        taskIds.remove(packageName)
        components.remove(packageName)
    }

    fun moveToFront(context: Context, packageName: String): Boolean {
        taskIds[packageName]?.let { id ->
            if (moveTaskId(context, id)) {
                WajihaLog.i(
                    WajihaTags.DISPLAY,
                    "SessionTaskRegistry: moveTaskToFront taskId=$id pkg=$packageName"
                )
                return true
            }
            taskIds.remove(packageName)
        }
        TopDisplayTaskResolver.taskIdForPackage(context, packageName)?.let { id ->
            taskIds[packageName] = id
            if (moveTaskId(context, id)) return true
        }
        components[packageName]?.let { component ->
            if (TopDisplayTaskResolver.reorderComponentToFront(context, component)) return true
        }
        return TopDisplayTaskResolver.reorderPackageToFront(context, packageName)
    }

    private fun moveTaskId(context: Context, taskId: Int): Boolean =
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.moveTaskToFront(taskId, 0)
            true
        } catch (_: Exception) {
            false
        }
}
