package com.wajiha.log

import android.os.SystemClock
import android.util.Log

internal actual fun currentTimeMs(): Long = SystemClock.elapsedRealtime()

internal actual fun platformLog(
    level: WajihaLogLevel,
    tag: String,
    message: String,
) {
    when (level) {
        WajihaLogLevel.DEBUG -> Log.d(tag, message)
        WajihaLogLevel.INFO -> Log.i(tag, message)
        WajihaLogLevel.WARN -> Log.w(tag, message)
    }
}
