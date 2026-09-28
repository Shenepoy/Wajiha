package com.wajiha.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import com.wajiha.ui.components.gamepad.GamepadButton
import com.wajiha.ui.components.gamepad.LocalSettingRowMinHeight
import com.wajiha.ui.components.gamepad.SettingSectionFocusRestorer
import com.wajiha.ui.components.gamepad.WajihaSettingPanel
import com.wajiha.ui.theme.WajihaSpacing

/**
 * Canonical Settings folder window metrics. All folder screens (Settings,
 * System, Game Info, Platform) must use these — do not invent parallel gutters.
 */
object WajihaFolderChromeMetrics {
    val horizontalPadding: Dp = WajihaSpacing.sm
    val topPadding: Dp = WajihaSpacing.sm
    val panelBottomPadding: Dp = WajihaSpacing.md
    val tabBackGap: Dp = WajihaSpacing.sm
}

/**
 * Settings-style folder chrome: optional outlined Back + [FolderTabRow] with a
 * shared seam into [WajihaSettingPanel] (`folderPanel = true`).
 *
 * Pair with section content only inside [content] — do not nest another panel
 * or apply extra horizontal padding around this composable.
 *
 * @param onBack When non-null, shows the outlined Back control (dual-display /
 *   Settings). When null, tabs span the full width (single-display with a
 *   toolbar Back).
 */
@Composable
fun WajihaFolderSettingChrome(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    scrollable: Boolean = true,
    focusRestorer: SettingSectionFocusRestorer? = null,
    /**
     * Folder panel fill. Default surface container; [Color.Transparent] lets a
     * hero backdrop show through (Now Running).
     */
    panelColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = WajihaFolderChromeMetrics.horizontalPadding),
    ) {
        val folderOutline = folderChromeOutlineColor()
        val folderEdge = folderChromeBorder().width
        var chromeCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
        var selectedTabCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .zIndex(1f)
                    .padding(top = WajihaFolderChromeMetrics.topPadding)
                    .onGloballyPositioned { chromeCoords = it }
                    .drawWithContent {
                        drawContent()
                        val chrome = chromeCoords
                        val tabCoords = selectedTabCoords
                        val tab =
                            if (chrome != null &&
                                tabCoords != null &&
                                chrome.isAttached &&
                                tabCoords.isAttached
                            ) {
                                chrome.localBoundingBoxOf(tabCoords, clipBounds = false)
                            } else {
                                null
                            }
                        val stroke = folderEdge.toPx()
                        drawFolderTopEdge(
                            color = folderOutline,
                            seamY = size.height - stroke / 2f,
                            width = size.width,
                            selectedTab = tab,
                            strokeWidth = stroke,
                        )
                    },
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(WajihaFolderChromeMetrics.tabBackGap),
        ) {
            if (onBack != null) {
                GamepadButton(
                    text = "Back",
                    onClick = onBack,
                    outlined = true,
                    gamepadFocusable = false,
                    sound = null,
                )
            }
            FolderTabRow(
                tabs = tabs,
                selectedIndex = selectedIndex,
                onSelect = onSelect,
                minHeight = LocalSettingRowMinHeight.current,
                showTopEdge = false,
                onSelectedTabCoordinates = { selectedTabCoords = it },
                modifier =
                    Modifier
                        .weight(1f)
                        .focusProperties { canFocus = false },
            )
        }

        WajihaSettingPanel(
            folderPanel = true,
            scrollable = scrollable,
            focusRestorer = focusRestorer,
            containerColor = panelColor,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = WajihaFolderChromeMetrics.panelBottomPadding),
            sectionContent = content,
        )
    }
}
