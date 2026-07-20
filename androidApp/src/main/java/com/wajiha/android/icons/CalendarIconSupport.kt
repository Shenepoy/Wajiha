package com.wajiha.android.icons

import java.util.Calendar

/** Detects calendar launcher icons so cache keys include the current day. */
object CalendarIconSupport {
    private val knownPackages =
        setOf(
            "com.google.android.calendar",
            "com.android.calendar",
            "com.samsung.android.calendar",
            "com.microsoft.office.outlook",
            "com.xiaomi.calendar",
            "com.huawei.calendar",
            "com.oneplus.calendar",
            "com.simplemobiletools.calendar.pro",
            "org.lineageos.etar",
            "ws.xsoh.etar",
        )

    fun isCalendarApp(
        packageName: String,
        activityName: String,
    ): Boolean {
        val pkg = packageName.lowercase()
        if (pkg in knownPackages) return true
        if (pkg.contains("calendar")) return true
        return activityName.lowercase().contains("calendar")
    }

    /** Day-of-year key so icons refresh when the date changes (and on drawer reload). */
    fun cacheDayKey(
        packageName: String,
        activityName: String,
    ): Int {
        if (!isCalendarApp(packageName, activityName)) return 0
        val cal = Calendar.getInstance()
        return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    }
}
