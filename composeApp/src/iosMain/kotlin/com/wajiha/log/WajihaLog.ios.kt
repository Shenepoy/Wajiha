package com.wajiha.log

/** Monotonic-ish tick for rate limits; iOS has no file sink. */
private var iosTickMs: Long = 0L

internal actual fun currentTimeMs(): Long {
    iosTickMs += 50L
    return iosTickMs
}

internal actual fun platformLog(
    level: WajihaLogLevel,
    tag: String,
    message: String,
) {
    // Gating is entirely in WajihaLog.emit / WajihaLogGate — do not re-filter here
    // or setKindEnabled(kind, true) cannot unlock DEBUG on iOS.
    println("$level/$tag: $message")
}
