package com.wajiha.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Icons not shipped in material-icons-core (e.g. Open In New Down).
 * Paths match Material Symbols outlined 24dp glyphs.
 */
object WajihaIcons {
    val OpenInNew: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "OpenInNew",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero,
                ) {
                    moveTo(5f, 21f)
                    quadTo(4.175f, 21f, 3.587f, 20.413f)
                    quadTo(3f, 19.825f, 3f, 19f)
                    lineTo(3f, 5f)
                    quadTo(3f, 4.175f, 3.587f, 3.587f)
                    quadTo(4.175f, 3f, 5f, 3f)
                    lineTo(12f, 3f)
                    lineTo(12f, 5f)
                    lineTo(5f, 5f)
                    lineTo(5f, 19f)
                    lineTo(19f, 19f)
                    lineTo(19f, 12f)
                    lineTo(21f, 12f)
                    lineTo(21f, 19f)
                    quadTo(21f, 19.825f, 20.413f, 20.413f)
                    quadTo(19.825f, 21f, 19f, 21f)
                    lineTo(5f, 21f)
                    close()
                    moveTo(9.7f, 15.7f)
                    lineTo(8.3f, 14.3f)
                    lineTo(17.6f, 5f)
                    lineTo(14f, 5f)
                    lineTo(14f, 3f)
                    lineTo(21f, 3f)
                    lineTo(21f, 10f)
                    lineTo(19f, 10f)
                    lineTo(19f, 6.4f)
                    lineTo(9.7f, 15.7f)
                    close()
                }
            }.build()
    }

    val OpenInNewDown: ImageVector by lazy {
        ImageVector
            .Builder(
                name = "OpenInNewDown",
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            ).apply {
                path(
                    fill = SolidColor(Color.Black),
                    pathFillType = PathFillType.NonZero,
                ) {
                    moveTo(5f, 21f)
                    quadTo(4.175f, 21f, 3.587f, 20.413f)
                    quadTo(3f, 19.825f, 3f, 19f)
                    lineTo(3f, 5f)
                    quadTo(3f, 4.175f, 3.587f, 3.587f)
                    quadTo(4.175f, 3f, 5f, 3f)
                    lineTo(19f, 3f)
                    quadTo(19.825f, 3f, 20.413f, 3.587f)
                    quadTo(21f, 4.175f, 21f, 5f)
                    lineTo(21f, 12f)
                    lineTo(19f, 12f)
                    lineTo(19f, 5f)
                    lineTo(5f, 5f)
                    lineTo(5f, 19f)
                    lineTo(12f, 19f)
                    lineTo(12f, 21f)
                    lineTo(5f, 21f)
                    close()
                    moveTo(14f, 21f)
                    lineTo(14f, 19f)
                    lineTo(17.6f, 19f)
                    lineTo(8.3f, 9.7f)
                    lineTo(9.7f, 8.3f)
                    lineTo(19f, 17.6f)
                    lineTo(19f, 14f)
                    lineTo(21f, 14f)
                    lineTo(21f, 21f)
                    lineTo(14f, 21f)
                    close()
                }
            }.build()
    }
}
