package com.wajiha.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.wajiha.input.GamepadHintButton
import com.wajiha.input.GamepadKeys
import com.wajiha.input.GamepadLayers
import com.wajiha.input.GamepadNavHost
import com.wajiha.input.GamepadNavItem
import com.wajiha.input.GamepadNavMode
import com.wajiha.input.GamepadOverlayLayer
import com.wajiha.input.rememberGamepadNavController
import com.wajiha.platform.SystemControls
import com.wajiha.state.DualScreenState
import com.wajiha.state.DualScreenStore
import com.wajiha.state.MenuRouteSnapshot
import com.wajiha.state.SystemNotification
import com.wajiha.state.SystemNotificationKind
import com.wajiha.state.SystemNotificationStore
import com.wajiha.ui.components.WajihaEmptyState
import com.wajiha.ui.components.gamepad.GamepadList
import com.wajiha.ui.components.gamepad.WajihaGlyphAction
import com.wajiha.ui.theme.WajihaAlphas
import com.wajiha.ui.theme.WajihaIconSize
import com.wajiha.ui.theme.WajihaMotion
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import kotlinx.coroutines.delay
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import kotlin.time.ExperimentalTime

private const val NotificationModalLayerId = "modal_system_notifications"
private const val PeekDurationMs = 4_000L
private val NotificationPanelMaxHeight = 220.dp

/**
 * Notch size for settings-hero surfaces that carve around the status pill.
 * Zero when the full pill is hidden (sliver / non-settings).
 */
data class StatusChromeCarveReserve(
    val width: Dp = 0.dp,
    val height: Dp = 0.dp,
) {
    val isActive: Boolean get() = width > 0.dp && height > 0.dp
}

val LocalStatusChromeCarveReserve =
    compositionLocalOf { StatusChromeCarveReserve() }

/**
 * Top-right status chrome on the hero screen: clock, connection, battery,
 * and notification indicator. R2 opens the in-app notification panel.
 *
 * When [reserveTopEnd] is true (hero chrome owns the corner), the pill tucks
 * to a sliver until a notification peeks or the panel opens.
 *
 * [onFullPillSizeChanged] reports the full chip size so settings hero can carve
 * a top-end notch; `DpSize.Zero` while the pill is tucked.
 */
@Composable
fun BoxScope.TopStatusBar(
    reserveTopEnd: Boolean = false,
    onFullPillSizeChanged: (DpSize) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val controls = koinInject<SystemControls>()
    val notifications = koinInject<SystemNotificationStore>()
    val dualStore = koinInject<DualScreenStore>()
    val status by controls.status.collectAsState()
    val items by notifications.notifications.collectAsState()
    val panelOpen by notifications.panelOpen.collectAsState()
    val peekRequestId by notifications.peekRequestId.collectAsState()
    val dualState by dualStore.state.collectAsState()
    val menuRoute by dualStore.menuRoute.collectAsState()
    var clock by remember { mutableStateOf(currentStatusTimeText()) }
    var peeking by remember { mutableStateOf(false) }
    var previousMenuRoute by remember { mutableStateOf<MenuRouteSnapshot?>(null) }
    var wasReservingTopEnd by remember { mutableStateOf(reserveTopEnd) }

    LaunchedEffect(Unit) {
        while (true) {
            controls.refreshStatus()
            clock = currentStatusTimeText()
            delay(1_000)
        }
    }

    LaunchedEffect(dualState) {
        if (dualState == DualScreenState.GameRunning ||
            dualState == DualScreenState.BlackoutSecondary
        ) {
            notifications.closePanel()
        }
    }

    LaunchedEffect(menuRoute) {
        val previous = previousMenuRoute
        previousMenuRoute = menuRoute
        if (previous != null && previous != menuRoute) {
            notifications.closePanel()
        }
    }

    LaunchedEffect(reserveTopEnd) {
        if (wasReservingTopEnd && !reserveTopEnd) {
            notifications.closePanel()
        }
        wasReservingTopEnd = reserveTopEnd
    }

    LaunchedEffect(peekRequestId) {
        if (peekRequestId == 0L) return@LaunchedEffect
        peeking = true
        delay(PeekDurationMs)
        peeking = false
    }

    val unread = items.count { !it.read }
    val showFullPill = !reserveTopEnd || panelOpen || peeking
    val batteryLabel = buildBatteryLabel(status.batteryPercent, status.charging)
    val density = LocalDensity.current
    val reportPillSize = rememberUpdatedState(onFullPillSizeChanged)

    LaunchedEffect(showFullPill) {
        if (!showFullPill) {
            reportPillSize.value(DpSize.Zero)
        }
    }

    Column(
        modifier =
            modifier
                .align(Alignment.TopEnd)
                .zIndex(8f)
                .padding(top = WajihaSpacing.sm, end = if (showFullPill) WajihaSpacing.md else 0.dp)
                .widthIn(max = 320.dp),
        horizontalAlignment = Alignment.End,
    ) {
        AnimatedContent(
            targetState = showFullPill,
            transitionSpec = {
                fadeIn(WajihaMotion.fadeInSpec()) togetherWith fadeOut(WajihaMotion.fadeOutSpec())
            },
            label = "status-pill",
        ) { full ->
            if (full) {
                StatusBarChip(
                    clock = clock,
                    wifiEnabled = status.wifiEnabled,
                    bluetoothEnabled = status.bluetoothEnabled,
                    battery = batteryLabel,
                    charging = status.charging,
                    unread = unread,
                    hasNotifications = items.isNotEmpty(),
                    onClick = { notifications.togglePanel() },
                    modifier =
                        Modifier.onSizeChanged { size ->
                            reportPillSize.value(
                                with(density) {
                                    DpSize(size.width.toDp(), size.height.toDp())
                                },
                            )
                        },
                )
            } else {
                StatusBarSliver(onClick = { notifications.openPanel() })
            }
        }

        AnimatedVisibility(
            visible = panelOpen,
            enter =
                fadeIn(WajihaMotion.fadeInSpec()) +
                    slideInVertically(animationSpec = WajihaMotion.fadeInSpec()) { -12 },
            exit =
                fadeOut(WajihaMotion.fadeOutSpec()) +
                    slideOutVertically(animationSpec = WajihaMotion.fadeOutSpec()) { -12 },
        ) {
            NotificationPanel(
                items = items,
                onDismiss = notifications::closePanel,
                onClearAll = notifications::clearAll,
                onRemove = notifications::dismiss,
            )
        }
    }
}

@Composable
private fun StatusBarSliver(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier =
            Modifier
                .width(WajihaSpacing.sm)
                .height(WajihaIconSize.xl)
                .clip(WajihaShapes.chip)
                .background(scheme.surfaceContainerLow)
                .border(
                    WajihaSpacing.folderEdge,
                    scheme.outline.copy(alpha = WajihaAlphas.outlineSubtle),
                    WajihaShapes.chip,
                ).pointerInput(onClick) {
                    detectTapGestures { onClick() }
                },
    )
}

@Composable
private fun StatusBarChip(
    clock: String,
    wifiEnabled: Boolean,
    bluetoothEnabled: Boolean,
    battery: String,
    charging: Boolean,
    unread: Int,
    hasNotifications: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier =
            modifier
                .clip(WajihaShapes.chip)
                .background(scheme.surfaceContainerLow)
                .border(
                    WajihaSpacing.folderEdge,
                    scheme.outline.copy(alpha = WajihaAlphas.outlineSubtle),
                    WajihaShapes.chip,
                ).pointerInput(onClick) {
                    detectTapGestures { onClick() }
                }.padding(horizontal = WajihaSpacing.sm, vertical = WajihaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
    ) {
        Text(
            text = clock,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        Icon(
            imageVector = StatusWifiIcon,
            contentDescription = if (wifiEnabled) "Wi‑Fi on" else "Wi‑Fi off",
            tint = if (wifiEnabled) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Icon(
            imageVector = StatusBluetoothIcon,
            contentDescription = if (bluetoothEnabled) "Bluetooth on" else "Bluetooth off",
            tint = if (bluetoothEnabled) scheme.onSurface else scheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = battery,
            style = MaterialTheme.typography.labelSmall,
            color = if (charging) scheme.tertiary else scheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.micro),
        ) {
            Icon(
                imageVector =
                    if (hasNotifications) {
                        Icons.Filled.Notifications
                    } else {
                        Icons.Outlined.Notifications
                    },
                contentDescription = "Notifications",
                tint = if (unread > 0) scheme.tertiary else scheme.onSurface,
                modifier = Modifier.size(14.dp),
            )
            if (unread > 0) {
                Text(
                    text = unread.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.tertiary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun NotificationPanel(
    items: List<SystemNotification>,
    onDismiss: () -> Unit,
    onClearAll: () -> Unit,
    onRemove: (Long) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val navController =
        rememberGamepadNavController(
            mode = GamepadNavMode.Vertical,
            onBack = {
                onDismiss()
                true
            },
        )
    val latestHandler =
        rememberUpdatedState<(KeyEvent) -> Boolean>(
            newValue = { event ->
                if (navController.handleKeyEvent(event)) return@rememberUpdatedState true
                if (items.isNotEmpty() && GamepadKeys.isX(event.type, event.key)) {
                    onClearAll()
                    return@rememberUpdatedState true
                }
                // Bridge dispatches D-pad/A/X/Y/L1/R1 down the layer stack when the
                // top handler returns false — eat those so the grid/settings under
                // us cannot move (empty list and list edges used to leak).
                isNotificationStolenPreviewKey(event)
            },
        )

    DisposableEffect(Unit) {
        val handler: (KeyEvent) -> Boolean = { event -> latestHandler.value(event) }
        GamepadLayers.stack.setPreviewHandler(NotificationModalLayerId, handler)
        onDispose {
            GamepadLayers.stack.setPreviewHandler(NotificationModalLayerId, null)
        }
    }

    LaunchedEffect(items.size) {
        navController.focusState.focusedIndex = 0
    }

    GamepadOverlayLayer(
        layerId = NotificationModalLayerId,
        onDismiss = onDismiss,
        modifier =
            Modifier
                .padding(top = WajihaSpacing.sm)
                .fillMaxWidth(),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .border(
                        width = WajihaSpacing.folderEdge,
                        color = scheme.outline.copy(alpha = WajihaAlphas.divider),
                        shape = WajihaShapes.card,
                    ),
            shape = WajihaShapes.card,
            color = scheme.surfaceContainerLow,
        ) {
            GamepadNavHost(
                controller = navController,
                interceptKeys = false,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(WajihaSpacing.sm),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Notifications",
                            style = MaterialTheme.typography.titleSmall,
                            color = scheme.onSurface,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.xs),
                        ) {
                            if (items.isNotEmpty()) {
                                WajihaGlyphAction(
                                    button = GamepadHintButton.X,
                                    label = "Clear",
                                    onClick = onClearAll,
                                    outlined = true,
                                    gamepadFocusable = false,
                                )
                            }
                            WajihaGlyphAction(
                                button = GamepadHintButton.R2,
                                label = "To close",
                                glyphAtEnd = true,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(WajihaSpacing.xs))

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(max = NotificationPanelMaxHeight),
                    ) {
                        GamepadList(
                            items = items,
                            key = { it.id },
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(0.dp),
                            emptyContent = {
                                WajihaEmptyState(
                                    title = "No system notifications",
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                        ) { item ->
                            GamepadNavItem(
                                onActivate = { onRemove(item.id) },
                                itemId = item.id,
                            ) {
                                NotificationRow(item = item)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(item: SystemNotification) {
    val scheme = MaterialTheme.colorScheme
    val accent =
        when (item.kind) {
            SystemNotificationKind.Success -> scheme.primary
            SystemNotificationKind.Warning -> scheme.tertiary
            SystemNotificationKind.Error -> scheme.error
            SystemNotificationKind.Info -> scheme.onSurfaceVariant
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(WajihaShapes.chip)
                .background(scheme.surfaceVariant.copy(alpha = WajihaAlphas.surfaceMuted))
                .padding(horizontal = WajihaSpacing.sm, vertical = WajihaSpacing.xs),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(top = 5.dp)
                    .size(WajihaSpacing.smHalf)
                    .clip(WajihaShapes.pill)
                    .background(accent),
        )
        Spacer(modifier = Modifier.width(WajihaSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.body,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Keys [tryDispatchTopLayerPreviewKey] may forward past us if we return false.
 * R2 stays with [LauncherTriggerActions]; B/Back is handled by overlay BackHandler.
 */
private fun isNotificationStolenPreviewKey(event: KeyEvent): Boolean {
    val type = event.type
    val key = event.key
    return GamepadKeys.isUp(type, key) ||
        GamepadKeys.isDown(type, key) ||
        GamepadKeys.isLeft(type, key) ||
        GamepadKeys.isRight(type, key) ||
        GamepadKeys.isConfirm(type, key) ||
        GamepadKeys.isX(type, key) ||
        GamepadKeys.isY(type, key) ||
        GamepadKeys.isL1(type, key) ||
        GamepadKeys.isR1(type, key)
}

private fun buildBatteryLabel(
    percent: Int,
    charging: Boolean,
): String {
    if (percent < 0) return "—%"
    return buildString {
        append(percent)
        append('%')
        if (charging) append('⚡')
    }
}

@OptIn(ExperimentalTime::class)
private fun currentStatusTimeText(): String {
    val dateTime =
        kotlin.time.Clock.System
            .now()
            .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    val h = dateTime.hour.toString().padStart(2, '0')
    val m = dateTime.minute.toString().padStart(2, '0')
    return "$h:$m"
}

/** Compact wifi arcs — material-icons-core has no Wifi glyph. */
private val StatusWifiIcon: ImageVector by lazy {
    ImageVector
        .Builder(
            name = "StatusWifi",
            defaultWidth = WajihaIconSize.lg,
            defaultHeight = WajihaIconSize.lg,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(12f, 18.5f)
                curveToRelative(-0.83f, 0f, -1.5f, 0.67f, -1.5f, 1.5f)
                reflectiveCurveToRelative(0.67f, 1.5f, 1.5f, 1.5f)
                reflectiveCurveToRelative(1.5f, -0.67f, 1.5f, -1.5f)
                reflectiveCurveToRelative(-0.67f, -1.5f, -1.5f, -1.5f)
                close()
                moveTo(12f, 3f)
                curveTo(7.31f, 3f, 3.07f, 4.9f, 0.68f, 7.89f)
                lineTo(2.1f, 9.3f)
                curveTo(4.18f, 6.89f, 7.86f, 5.25f, 12f, 5.25f)
                reflectiveCurveToRelative(7.82f, 1.64f, 9.9f, 4.05f)
                lineToRelative(1.42f, -1.41f)
                curveTo(19.93f, 4.9f, 15.69f, 3f, 12f, 3f)
                close()
                moveTo(12f, 8.5f)
                curveToRelative(-3.03f, 0f, -5.78f, 1.23f, -7.76f, 3.21f)
                lineToRelative(1.42f, 1.41f)
                curveTo(7.31f, 11.47f, 9.53f, 10.5f, 12f, 10.5f)
                reflectiveCurveToRelative(4.69f, 0.97f, 6.34f, 2.62f)
                lineToRelative(1.42f, -1.41f)
                curveTo(17.78f, 9.73f, 15.03f, 8.5f, 12f, 8.5f)
                close()
                moveTo(12f, 14f)
                curveToRelative(-1.52f, 0f, -2.89f, 0.62f, -3.88f, 1.61f)
                lineTo(12f, 19.5f)
                lineToRelative(3.88f, -3.89f)
                curveTo(14.89f, 14.62f, 13.52f, 14f, 12f, 14f)
                close()
            }
        }.build()
}

/** Compact bluetooth rune — material-icons-core has no Bluetooth glyph. */
private val StatusBluetoothIcon: ImageVector by lazy {
    ImageVector
        .Builder(
            name = "StatusBluetooth",
            defaultWidth = WajihaIconSize.lg,
            defaultHeight = WajihaIconSize.lg,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(17.71f, 7.71f)
                lineTo(12f, 2f)
                horizontalLineToRelative(-1f)
                verticalLineToRelative(7.59f)
                lineTo(6.41f, 5f)
                lineTo(5f, 6.41f)
                lineTo(10.59f, 12f)
                lineTo(5f, 17.59f)
                lineTo(6.41f, 19f)
                lineTo(11f, 14.41f)
                verticalLineTo(22f)
                horizontalLineToRelative(1f)
                lineToRelative(5.71f, -5.71f)
                lineTo(13.41f, 12f)
                lineToRelative(4.3f, -4.29f)
                close()
                moveTo(13f, 5.83f)
                lineToRelative(1.88f, 1.88f)
                lineTo(13f, 9.59f)
                verticalLineTo(5.83f)
                close()
                moveTo(14.88f, 16.29f)
                lineTo(13f, 18.17f)
                verticalLineToRelative(-3.76f)
                lineToRelative(1.88f, 1.88f)
                close()
            }
        }.build()
}
