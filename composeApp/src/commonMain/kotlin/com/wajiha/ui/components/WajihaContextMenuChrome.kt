package com.wajiha.ui.components

import androidx.compose.ui.unit.Dp
import com.wajiha.ui.theme.WajihaIconSize
import com.wajiha.ui.theme.WajihaSpacing

/**
 * Shared App / Session / Game context-menu metrics.
 * Use [com.wajiha.ui.theme.WajihaColors.MenuScrim] for the dimmer (not HeroScrim).
 */
object WajihaContextMenuMetrics {
    val width: Dp = WajihaSpacing.contextMenuWidth
    val titleGap: Dp = WajihaSpacing.xs
    val rowHeight: Dp = WajihaSpacing.contextMenuRowHeight
    val nestedRowHeight: Dp = WajihaSpacing.contextMenuNestedRowHeight
    val iconSlot: Dp = WajihaIconSize.sm
}
