package com.wajiha.platform

import androidx.compose.ui.graphics.ImageBitmap

data class IconPackInfo(
    /** Empty string = system icons. */
    val packageName: String,
    val label: String,
    val icon: ImageBitmap? = null,
)

/** Android-only: list installed Nova/ADW-style icon packs for Settings. */
interface IconPackActions {
    suspend fun installedPacks(): List<IconPackInfo>
}
