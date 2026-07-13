package com.wajiha.ui.scraper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.wajiha.data.scraper.ScrapeApiLogEntry
import com.wajiha.input.GamepadOverlayLayer
import com.wajiha.ui.components.gamepad.GamepadButton

@Composable
fun ScrapeApiLogsDialog(
    visible: Boolean,
    entries: List<ScrapeApiLogEntry>,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
) {
    if (!visible) return
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.scrollToItem(entries.lastIndex)
        }
    }

    GamepadOverlayLayer(
        layerId = "dialog_scrape_api_logs",
        onDismiss = onDismiss,
        onConfirm = {
            onDismiss()
            true
        },
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Scraper API logs (${entries.size})") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 360.dp, max = 720.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Secrets redacted. Newest at bottom.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (entries.isEmpty()) {
                        Text(
                            text = "No API calls recorded yet. Run a scrape or credential test.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        ) {
                            items(entries, key = { "${it.atMs}-${it.url}-${it.status}" }) { entry ->
                                Text(
                                    text = entry.displayLine(),
                                    style =
                                        MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                        ),
                                    color =
                                        if (entry.ok) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                GamepadButton(text = "Close", onClick = onDismiss)
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GamepadButton(
                        text = "Clear",
                        onClick = onClear,
                        outlined = true,
                        enabled = entries.isNotEmpty(),
                    )
                }
            },
        )
    }
}
