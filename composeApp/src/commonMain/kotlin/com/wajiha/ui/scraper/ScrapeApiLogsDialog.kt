package com.wajiha.ui.scraper

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.wajiha.data.scraper.ScrapeApiLogEntry
import com.wajiha.input.FocusClaimSource
import com.wajiha.input.GamepadKeys
import com.wajiha.input.LocalFocusContinuityController
import com.wajiha.input.LocalFocusLayerId
import com.wajiha.input.wajihaGamepadFocus
import com.wajiha.ui.components.LocalUiFeedback
import com.wajiha.ui.components.WajihaEmptyState
import com.wajiha.ui.components.gamepad.GamepadSettingRow
import com.wajiha.ui.components.gamepad.LocalSettingRowMinHeight
import com.wajiha.ui.components.gamepad.SettingType
import com.wajiha.ui.components.gamepad.WajihaActionSetting
import com.wajiha.ui.components.gamepad.WajihaFieldMessage
import com.wajiha.ui.components.gamepad.WajihaFieldMessageSeverity
import com.wajiha.ui.components.gamepad.WajihaFieldMessageState
import com.wajiha.ui.components.gamepad.WajihaSettingDivider
import com.wajiha.ui.components.gamepad.WajihaSettingFullscreenPage
import com.wajiha.ui.components.gamepad.wajihaFocusIndicator
import com.wajiha.ui.components.softOutlineBorder
import com.wajiha.ui.theme.WajihaAlphas
import com.wajiha.ui.theme.WajihaColors
import com.wajiha.ui.theme.WajihaShapes
import com.wajiha.ui.theme.WajihaSpacing
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private enum class ApiLogFilter(
    val id: String,
    val label: String,
) {
    All("all", "All"),
    Failures("failures", "Failures"),
}

/**
 * Full-screen API log viewer hosted in the same Activity window as Settings.
 * Prefer this over a Compose [androidx.compose.ui.window.Dialog] so gamepad keys
 * stay on the secondary-display activity (Dialog windows break dual-display routing).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ScrapeApiLogsPage(
    entries: List<ScrapeApiLogEntry>,
    onBack: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    firstFocusRequester: FocusRequester? = null,
) {
    // Consume B / system back here so parent Batch scrape is not dismissed first.
    BackHandler(onBack = onBack)

    val failureCount = remember(entries) { entries.count { !it.ok } }
    WajihaSettingFullscreenPage(
        title = "API logs",
        description =
            buildString {
                append(entries.size)
                append(if (entries.size == 1) " call" else " calls")
                if (failureCount > 0) {
                    append(" · ")
                    append(failureCount)
                    append(if (failureCount == 1) " failure" else " failures")
                }
                append(" · secrets redacted")
            },
        onBack = onBack,
        scrollable = false,
        modifier = modifier,
    ) {
        ScrapeApiLogsContent(
            entries = entries,
            onClear = onClear,
            firstFocusRequester = firstFocusRequester,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ColumnScope.ScrapeApiLogsContent(
    entries: List<ScrapeApiLogEntry>,
    onClear: () -> Unit,
    firstFocusRequester: FocusRequester? = null,
) {
    var filter by remember { mutableStateOf(ApiLogFilter.All) }
    val filtered =
        remember(entries, filter) {
            when (filter) {
                ApiLogFilter.All -> entries
                ApiLogFilter.Failures -> entries.filter { !it.ok }
            }.asReversed()
        }
    val listState = rememberLazyListState()
    val filterFocus = firstFocusRequester ?: remember { FocusRequester() }
    val clearFocus = remember { FocusRequester() }
    val firstEntryFocus = remember { FocusRequester() }
    var expandedKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(entries.size, filter) {
        if (filtered.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        try {
            filterFocus.requestFocus()
        } catch (_: Exception) {
        }
    }

    BackHandler(enabled = expandedKey != null) {
        expandedKey = null
    }

    GamepadSettingRow(
        label = "Show",
        description = "Left / Right switches All and Failures.",
        type = SettingType.BinaryChoice,
        options = ApiLogFilter.entries.map { it.id to it.label },
        selected = filter.id,
        onSelect = { id ->
            filter = ApiLogFilter.entries.firstOrNull { it.id == id } ?: ApiLogFilter.All
            expandedKey = null
        },
        focusRequester = filterFocus,
        focusId = "apiLogs:filter",
    )

    if (entries.isNotEmpty()) {
        WajihaActionSetting(
            label = "Clear log",
            actionLabel = "Clear",
            onClick = {
                expandedKey = null
                onClear()
            },
            focusRequester = clearFocus,
        )
    }

    when {
        entries.isEmpty() -> {
            WajihaEmptyState(
                title = "No API calls yet",
                subtitle = "Run a batch scrape or credential test to capture requests here.",
            )
        }

        filtered.isEmpty() -> {
            WajihaFieldMessage(
                WajihaFieldMessageState(
                    text = "No failures in the current log.",
                    severity = WajihaFieldMessageSeverity.Supporting,
                ),
            )
        }

        else -> {
            WajihaFieldMessage(
                WajihaFieldMessageState(
                    text = "Newest first. A expands a call; B collapses detail before Back.",
                    severity = WajihaFieldMessageSeverity.Supporting,
                ),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(WajihaSpacing.xs),
            ) {
                itemsIndexed(
                    items = filtered,
                    key = { _, entry -> entry.listKey() },
                ) { index, entry ->
                    if (index > 0) {
                        WajihaSettingDivider()
                    }
                    val key = entry.listKey()
                    ApiLogEntryRow(
                        entry = entry,
                        expanded = expandedKey == key,
                        focusRequester = if (index == 0) firstEntryFocus else null,
                        focusId = "apiLogs:entry:$key",
                        onToggle = {
                            expandedKey = if (expandedKey == key) null else key
                        },
                        onCollapseRequest = { expandedKey = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun ApiLogEntryRow(
    entry: ScrapeApiLogEntry,
    expanded: Boolean,
    focusId: Any,
    onToggle: () -> Unit,
    onCollapseRequest: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val feedback = LocalUiFeedback.current
    val continuity = LocalFocusContinuityController.current
    val layerId = LocalFocusLayerId.current
    val localFocusRequester = remember { FocusRequester() }
    val resolvedFocusRequester = focusRequester ?: localFocusRequester
    val anchor =
        remember(continuity, layerId, focusId) {
            continuity?.takeIf { layerId.isNotEmpty() }?.anchor(focusId, layerId)
        }
    val rowShape = WajihaShapes.focus
    val parsed = remember(entry.url) { entry.parseUrlParts() }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .then(
                    if (!entry.ok) {
                        Modifier.background(
                            MaterialTheme.colorScheme.error.copy(alpha = WajihaAlphas.divider),
                        )
                    } else {
                        Modifier
                    },
                ).wajihaFocusIndicator(
                    highlighted = focused,
                    shape = rowShape,
                    focusAnchor = anchor,
                ).focusRequester(resolvedFocusRequester)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused && anchor != null) {
                        continuity?.claim(anchor, FocusClaimSource.Compose)
                    }
                }.wajihaGamepadFocus()
                .onPreviewKeyEvent { event ->
                    when {
                        GamepadKeys.isConfirm(event.type, event.key) -> {
                            feedback.confirm()
                            onToggle()
                            true
                        }

                        expanded && GamepadKeys.isBack(event.type, event.key) -> {
                            feedback.back()
                            onCollapseRequest()
                            true
                        }

                        else -> {
                            false
                        }
                    }
                }.pointerInput(entry.listKey()) {
                    detectTapGestures {
                        if (anchor != null) {
                            continuity?.claim(anchor, FocusClaimSource.Touch)
                        }
                        try {
                            resolvedFocusRequester.requestFocus()
                        } catch (_: Exception) {
                        }
                        feedback.confirm()
                        onToggle()
                    }
                }.padding(horizontal = WajihaSpacing.xs, vertical = WajihaSpacing.xs),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = LocalSettingRowMinHeight.current),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(WajihaSpacing.sm),
        ) {
            ApiLogStatusBadge(entry = entry)
            Text(
                text = entry.method.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = parsed.host.ifBlank { entry.url },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (parsed.pathQuery.isNotBlank()) {
                    Text(
                        text = parsed.pathQuery,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) 3 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatLogClock(entry.atMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = entry.metaLine(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = WajihaSpacing.xs)
                        .clip(WajihaShapes.button)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(
                                alpha = WajihaAlphas.surfaceMuted,
                            ),
                        ).padding(WajihaSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(WajihaSpacing.xs),
            ) {
                Text(
                    text = entry.url,
                    style =
                        MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (!entry.detail.isNullOrBlank()) {
                    Text(
                        text = entry.detail,
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (entry.ok) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun ApiLogStatusBadge(entry: ScrapeApiLogEntry) {
    val label = entry.status?.toString() ?: "ERR"
    val bg: Color
    val fg: Color
    when {
        entry.ok && (entry.status == null || entry.status in 200..299) -> {
            bg = WajihaColors.StatusGreen.copy(alpha = 0.22f)
            fg = WajihaColors.StatusGreen
        }

        entry.ok -> {
            bg = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.22f)
            fg = MaterialTheme.colorScheme.tertiary
        }

        else -> {
            bg = MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
            fg = MaterialTheme.colorScheme.error
        }
    }
    Box(
        modifier =
            Modifier
                .clip(WajihaShapes.button)
                .background(bg)
                .border(border = softOutlineBorder(alpha = WajihaAlphas.outlineMuted), shape = WajihaShapes.button)
                .padding(horizontal = WajihaSpacing.sm, vertical = WajihaSpacing.micro),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = fg,
        )
    }
}

private data class ParsedApiUrl(
    val host: String,
    val pathQuery: String,
)

private fun ScrapeApiLogEntry.listKey(): String = id.toString()

private fun ScrapeApiLogEntry.parseUrlParts(): ParsedApiUrl {
    val withoutScheme =
        url
            .removePrefix("https://")
            .removePrefix("http://")
    val slash = withoutScheme.indexOf('/')
    return if (slash < 0) {
        ParsedApiUrl(host = withoutScheme, pathQuery = "")
    } else {
        ParsedApiUrl(
            host = withoutScheme.substring(0, slash),
            pathQuery = withoutScheme.substring(slash),
        )
    }
}

private fun ScrapeApiLogEntry.metaLine(): String {
    val parts =
        buildList {
            elapsedMs?.let { add("${it}ms") }
            bytes?.let { add(formatByteCount(it)) }
            if (!ok) add("FAIL")
        }
    return parts.joinToString(" · ")
}

private fun formatByteCount(bytes: Int): String =
    when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        else -> "${bytes / (1024 * 1024)}MB"
    }

private fun formatLogClock(atMs: Long): String {
    val local =
        Instant
            .fromEpochMilliseconds(atMs)
            .toLocalDateTime(TimeZone.currentSystemDefault())
    val h = local.hour.toString().padStart(2, '0')
    val m = local.minute.toString().padStart(2, '0')
    val s = local.second.toString().padStart(2, '0')
    return "$h:$m:$s"
}
