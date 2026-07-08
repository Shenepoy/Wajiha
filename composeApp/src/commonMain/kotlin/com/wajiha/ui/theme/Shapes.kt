package com.wajiha.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object WajihaShapes {
    val tile = RoundedCornerShape(12.dp)
    val card = RoundedCornerShape(16.dp)
    val chip = RoundedCornerShape(20.dp)
    val dialog = RoundedCornerShape(20.dp)
    val focus = RoundedCornerShape(8.dp)

    /** Folder tab — rounded top, flat bottom to meet content panel. */
    val folderTab = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    )

    /** Folder content panel — flat top, rounded bottom and sides. */
    val folderPanel = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = 16.dp,
        bottomEnd = 16.dp
    )
}
