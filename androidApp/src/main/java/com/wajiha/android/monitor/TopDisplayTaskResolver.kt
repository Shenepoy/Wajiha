package com.wajiha.android.monitor

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.Display
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags

/**
 * Finds emulator tasks on the top (default) display via [ActivityTaskManager.getTasks].
 * [ActivityManager.getRunningTasks] supplements with background (visible=false) tasks
 * that ATM omits while Wajiha owns the bottom launcher — required for multi-session
 * switch and alive checks on Thor.
 */
internal object TopDisplayTaskResolver {

    fun taskIdForPackage(
        context: Context,
        packageName: String,
        displayId: Int = Display.DEFAULT_DISPLAY
    ): Int? {
        tasksOnDisplay(context, displayId)
            .firstOrNull { task -> taskPackage(task) == packageName }
            ?.let { return taskId(it) }
        return allRunningTasks(context)
            .firstOrNull { task -> taskPackage(task) == packageName }
            ?.let { taskId(it) }
    }

    fun taskIdForActivityClass(
        context: Context,
        activityClass: Class<*>,
        displayId: Int = Display.DEFAULT_DISPLAY
    ): Int? {
        val name = activityClass.name
        tasksOnDisplay(context, displayId)
            .firstOrNull { task ->
                task.topActivity?.className == name || task.baseActivity?.className == name
            }
            ?.let { return taskId(it) }
        return allRunningTasks(context)
            .firstOrNull { task ->
                task.topActivity?.className == name || task.baseActivity?.className == name
            }
            ?.let { taskId(it) }
    }

    /** All packages with a running task (any visibility / display). */
    fun packagesWithTasks(context: Context): Set<String> =
        (tasksOnDisplay(context) + allRunningTasks(context))
            .mapNotNull { taskPackage(it) }
            .toSet()

    /** Bring an existing emulation activity back without cold-starting. */
    fun reorderPackageToFront(context: Context, packageName: String): Boolean {
        val task = allRunningTasks(context).firstOrNull { taskPackage(it) == packageName }
            ?: return false
        val component = task.topActivity ?: task.baseActivity ?: return false
        return reorderComponentToFront(context, component)
    }

    fun reorderComponentToFront(context: Context, component: ComponentName): Boolean =
        try {
            val intent = Intent().setComponent(component).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
            val options = ActivityOptions.makeBasic()
                .setLaunchDisplayId(Display.DEFAULT_DISPLAY)
            context.startActivity(intent, options.toBundle())
            WajihaLog.i(
                WajihaTags.DISPLAY,
                "reorderComponentToFront: ${component.flattenToShortString()}"
            )
            true
        } catch (e: Exception) {
            WajihaLog.w(
                WajihaTags.DISPLAY,
                "reorderComponentToFront: failed ${component.packageName} — ${e.message}"
            )
            false
        }

    fun tasksOnDisplay(
        context: Context,
        displayId: Int = Display.DEFAULT_DISPLAY
    ): List<ActivityManager.RunningTaskInfo> {
        val fromAtm = queryViaActivityTaskManager(displayId)
        if (fromAtm.isNotEmpty()) return fromAtm
        return allRunningTasks(context)
    }

    @Suppress("DEPRECATION")
    fun allRunningTasks(context: Context): List<ActivityManager.RunningTaskInfo> =
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.getRunningTasks(100)
        } catch (_: Exception) {
            emptyList()
        }

    private fun queryViaActivityTaskManager(
        displayId: Int
    ): List<ActivityManager.RunningTaskInfo> = try {
        val atmClass = Class.forName("android.app.ActivityTaskManager")
        val service = atmClass.getMethod("getService").invoke(null)
        val getTasks = service.javaClass.getMethod(
            "getTasks",
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        @Suppress("UNCHECKED_CAST")
        (getTasks.invoke(service, 100, false, false, displayId)
            as? List<ActivityManager.RunningTaskInfo>).orEmpty()
    } catch (_: Exception) {
        emptyList()
    }

    private fun taskPackage(task: ActivityManager.RunningTaskInfo): String? =
        task.topActivity?.packageName ?: task.baseActivity?.packageName

    @Suppress("DEPRECATION")
    private fun taskId(task: ActivityManager.RunningTaskInfo): Int =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            task.taskId
        } else {
            task.id
        }
}
