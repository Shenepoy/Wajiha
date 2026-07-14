package com.wajiha.domain.scan

/** Folder-walk depth used by [com.wajiha.platform.RomScanner] for library scans. */
object ScanDepth {
    const val NORMAL = 3
    const val DEEP = 15

    fun forDeepScan(deepScan: Boolean): Int = if (deepScan) DEEP else NORMAL
}
