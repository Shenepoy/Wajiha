package com.wajiha.log

/**
 * Cross-platform logging facade. On Android, routes to [android.util.Log] so
 * agents can filter with `adb logcat -s Wajiha/Display:D Wajiha/Gamepad:D …`.
 */
expect object WajihaLog {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
}

object WajihaTags {
    const val DISPLAY = "Wajiha/Display"
    const val GAMEPAD = "Wajiha/Gamepad"
    const val LAUNCH = "Wajiha/Launch"
    const val NOW_PLAYING = "Wajiha/NowPlaying"
    const val SCRAPE = "Wajiha/Scrape"
    const val LIBRARY = "Wajiha/Library"
}
