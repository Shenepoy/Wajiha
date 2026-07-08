package com.wajiha.ui.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wajiha.platform.LaunchableApp

/** App drawer: same NeoStation select/confirm model as game tiles. */
@Composable
fun AppDrawerScreen(
    apps: List<LaunchableApp>,
    onLoad: () -> Unit,
    onLaunch: (String) -> Unit,
    onBack: () -> Unit,
    onFocusChange: (LaunchableApp?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) { onLoad() }

    var selectedPackage by remember { mutableStateOf<String?>(null) }
    var aTileHasFocus by remember { mutableStateOf(false) }

    LaunchedEffect(apps.size, selectedPackage) {
        val focused = apps.firstOrNull { it.packageName == selectedPackage }
        onFocusChange(focused)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onPreviewKeyEvent { event ->
                val pkg = selectedPackage
                if (
                    aTileHasFocus &&
                    pkg != null &&
                    isConfirmKeyDown(event.type, event.key)
                ) {
                    onLaunch(pkg)
                    true
                } else {
                    false
                }
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("< Back") }
            Text(
                text = "Apps",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(88.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(apps, key = { it.packageName }) { app ->
                AppTile(
                    app = app,
                    selected = app.packageName == selectedPackage,
                    onSelect = {
                        selectedPackage = app.packageName
                        onFocusChange(app)
                    },
                    onLaunch = { onLaunch(app.packageName) },
                    onTileFocusChanged = { focused ->
                        if (focused) {
                            aTileHasFocus = true
                            selectedPackage = app.packageName
                            onFocusChange(app)
                        } else {
                            aTileHasFocus = false
                        }
                    }
                )
            }
        }
    }
}

private fun isConfirmKeyDown(type: KeyEventType, key: Key): Boolean {
    if (type != KeyEventType.KeyDown) return false
    return key == Key.DirectionCenter ||
        key == Key.Enter ||
        key == Key.NumPadEnter ||
        key == Key.ButtonA
}

@Composable
private fun AppTile(
    app: LaunchableApp,
    selected: Boolean,
    onSelect: () -> Unit,
    onLaunch: () -> Unit,
    onTileFocusChanged: (Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val highlight = focused || selected
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (highlight) 2.dp else 0.dp,
                color = if (highlight) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.background
                },
                shape = RoundedCornerShape(12.dp)
            )
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                onTileFocusChanged(it.isFocused)
            }
            .focusable()
            .pointerInput(selected, focused) {
                detectTapGestures {
                    if (focused) {
                        onLaunch()
                    } else {
                        onSelect()
                        try {
                            focusRequester.requestFocus()
                        } catch (_: Exception) {
                        }
                    }
                }
            }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val icon = app.icon
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = app.label,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = app.label.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
