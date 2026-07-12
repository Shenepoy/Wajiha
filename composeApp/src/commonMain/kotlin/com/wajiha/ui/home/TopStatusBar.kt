package com.wajiha.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.wajiha.input.GamepadKeys
import com.wajiha.input.GamepadOverlayLayer
import com.wajiha.platform.SystemControls
import com.wajiha.state.DualScreenState
import com.wajiha.state.DualScreenStore
import com.wajiha.state.SystemNotification
import com.wajiha.state.SystemNotificationKind
import com.wajiha.state.SystemNotificationStore
import com.wajiha.ui.theme.WajihaColors
import com.wajiha.ui.theme.WajihaMotion
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import kotlin.time.ExperimentalTime
import kotlinx.datetime.toLocalDateTime

/**
 * Top-right status chrome on the hero screen: clock, connection, battery,
 * and notification indicator. R2 opens the in-app notification panel.
 */
@Composable
fun BoxScope.TopStatusBar(
    modifier: Modifier = Modifier
) {
    val controls = koinInject<SystemControls>()
    val notifications = koinInject<SystemNotificationStore>()
    val dualStore = koinInject<DualScreenStore>()
    val status by controls.status.collectAsState()
    val items by notifications.notifications.collectAsState()
    val panelOpen by notifications.panelOpen.collectAsState()
    val dualState by dualStore.state.collectAsState()
    var clock by remember { mutableStateOf(currentStatusTimeText()) }

    LaunchedEffect(Unit) {
        while (true) {
            controls.refreshStatus()
            clock = currentStatusTimeText()
            delay(1_000)
        }
    }

    LaunchedEffect(dualState) {
        if (dualState == DualScreenState.GameRunning) {
            notifications.closePanel()
        }
    }

    val unread = items.count { !it.read }
    val connectionLabel = buildConnectionLabel(status.wifiEnabled, status.bluetoothEnabled)
    val batteryLabel = buildBatteryLabel(status.batteryPercent, status.charging)

    Column(
        modifier = modifier
            .align(Alignment.TopEnd)
            .zIndex(8f)
            .padding(top = WajihaSpacing.sm, end = WajihaSpacing.md)
            .widthIn(max = 320.dp),
        horizontalAlignment = Alignment.End
    ) {
        StatusBarChip(
            clock = clock,
            connection = connectionLabel,
            battery = batteryLabel,
            unread = unread,
            hasNotifications = items.isNotEmpty(),
            onNotificationsClick = { notifications.togglePanel() }
        )

        AnimatedVisibility(
            visible = panelOpen,
            enter = fadeIn(WajihaMotion.fadeInSpec()) +
                slideInVertically(animationSpec = WajihaMotion.fadeInSpec()) { -12 },
            exit = fadeOut(WajihaMotion.fadeOutSpec()) +
                slideOutVertically(animationSpec = WajihaMotion.fadeOutSpec()) { -12 }
        ) {
            NotificationPanel(
                items = items,
                onDismiss = notifications::closePanel,
                onClearAll = notifications::clearAll,
                onRemove = notifications::dismiss
            )
        }
    }
}

@Composable
private fun StatusBarChip(
    clock: String,
    connection: String,
    battery: String,
    unread: Int,
    hasNotifications: Boolean,
    onNotificationsClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(WajihaShapes.chip)
            .background(WajihaColors.HeroScrim)
            .border(1.dp, scheme.outline.copy(alpha = 0.35f), WajihaShapes.chip)
            .padding(horizontal = WajihaSpacing.sm, vertical = WajihaSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm)
    ) {
        StatusSegment(clock)
        StatusDivider()
        StatusSegment(connection)
        StatusDivider()
        StatusSegment(battery)
        StatusDivider()
        Row(
            modifier = Modifier
                .clip(WajihaShapes.chip)
                .clickable(onClick = onNotificationsClick)
                .padding(horizontal = 2.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (hasNotifications) {
                    Icons.Filled.Notifications
                } else {
                    Icons.Outlined.Notifications
                },
                contentDescription = "Notifications",
                tint = if (unread > 0) scheme.tertiary else scheme.onSurface,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (unread > 0) unread.toString() else "0",
                style = MaterialTheme.typography.labelSmall,
                color = if (unread > 0) scheme.tertiary else scheme.onSurfaceVariant,
                fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun StatusSegment(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Clip
    )
}

@Composable
private fun StatusDivider() {
    Box(
        modifier = Modifier
            .size(width = 1.dp, height = 10.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    )
}

@Composable
private fun NotificationPanel(
    items: List<SystemNotification>,
    onDismiss: () -> Unit,
    onClearAll: () -> Unit,
    onRemove: (Long) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    GamepadOverlayLayer(
        layerId = "modal_system_notifications",
        onDismiss = onDismiss,
        onToggleKey = { event ->
            if (GamepadKeys.isR2(event.type, event.key)) {
                onDismiss()
                true
            } else {
                false
            }
        },
        modifier = Modifier
            .padding(top = WajihaSpacing.sm)
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(WajihaShapes.card)
                .background(scheme.surface.copy(alpha = 0.96f))
                .border(1.dp, scheme.outline.copy(alpha = 0.4f), WajihaShapes.card)
                .padding(WajihaSpacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (items.isNotEmpty()) {
                        TextButton(onClick = onClearAll) {
                            Text("Clear", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(
                        text = "R2 toggle",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }

            if (items.isEmpty()) {
                Text(
                    text = "No system notifications",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = WajihaSpacing.sm)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(WajihaSpacing.xs)
                ) {
                    items(items, key = { it.id }) { item ->
                        NotificationRow(item = item, onRemove = { onRemove(item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    item: SystemNotification,
    onRemove: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val accent = when (item.kind) {
        SystemNotificationKind.Success -> scheme.primary
        SystemNotificationKind.Warning -> scheme.tertiary
        SystemNotificationKind.Error -> scheme.error
        SystemNotificationKind.Info -> scheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(WajihaShapes.chip)
            .background(scheme.surfaceVariant.copy(alpha = 0.55f))
            .clickable(onClick = onRemove)
            .padding(horizontal = WajihaSpacing.sm, vertical = WajihaSpacing.xs),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(6.dp)
                .clip(WajihaShapes.pill)
                .background(accent)
        )
        Spacer(modifier = Modifier.width(WajihaSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.body,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun buildConnectionLabel(wifi: Boolean, bluetooth: Boolean): String = when {
    wifi && bluetooth -> "Wi‑Fi · BT"
    wifi -> "Wi‑Fi"
    bluetooth -> "BT"
    else -> "Offline"
}

private fun buildBatteryLabel(percent: Int, charging: Boolean): String {
    if (percent < 0) return "—%"
    return buildString {
        append(percent)
        append('%')
        if (charging) append('⚡')
    }
}

@OptIn(ExperimentalTime::class)
private fun currentStatusTimeText(): String {
    val dateTime = kotlin.time.Clock.System.now()
        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
    val h = dateTime.hour.toString().padStart(2, '0')
    val m = dateTime.minute.toString().padStart(2, '0')
    return "$h:$m"
}
