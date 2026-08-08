package com.wajiha.log

import android.os.SystemClock
import android.util.Log

internal actual fun currentTimeMs(): Long =
    try {
        SystemClock.elapsedRealtime()
    } catch (_: RuntimeException) {
        // androidHostTest JVM stubs throw for SystemClock; wall clock is fine for budgets/logs.
        System.currentTimeMillis()
    }

internal actual fun platformLog(
    level: WajihaLogLevel,
    tag: String,
    message: String,
) {
    try {
        when (level) {
            WajihaLogLevel.DEBUG -> Log.d(tag, message)
            WajihaLogLevel.INFO -> Log.i(tag, message)
            WajihaLogLevel.WARN -> Log.w(tag, message)
        }
    } catch (_: RuntimeException) {
        // androidHostTest JVM stubs throw for android.util.Log.
    }
}
