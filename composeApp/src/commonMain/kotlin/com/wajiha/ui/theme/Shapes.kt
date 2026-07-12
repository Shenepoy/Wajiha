package com.wajiha.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object WajihaShapes {
    /** Cover art / grid tiles — slight radius for artwork. */
    val tileCornerRadius = 8.dp
    val tile = RoundedCornerShape(tileCornerRadius)

    /** Content panels and popover surfaces. */
    val card = RoundedCornerShape(8.dp)

    /** Chips, segmented pills, badges — boxy interactive controls. */
    val chip = RoundedCornerShape(4.dp)

    /** 3DS suspended-software strip / compact overlay pills. */
    val pill = RoundedCornerShape(percent = 50)

    val dialog = RoundedCornerShape(8.dp)

    /** Buttons and focusable row chrome — matches focus ring corners. */
    val button = RoundedCornerShape(4.dp)

    val focusCornerRadius = 4.dp
    val focus = RoundedCornerShape(focusCornerRadius)

    /** Folder tab — rounded top, flat bottom to meet content panel. */
    val folderTab =
        RoundedCornerShape(
            topStart = 4.dp,
            topEnd = 4.dp,
            bottomStart = 0.dp,
            bottomEnd = 0.dp,
        )

    /** Folder content panel — flat top, rounded bottom and sides. */
    val folderPanel =
        RoundedCornerShape(
            topStart = 0.dp,
            topEnd = 0.dp,
            bottomStart = 4.dp,
            bottomEnd = 4.dp,
        )
}
