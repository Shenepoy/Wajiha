package com.wajiha.ui.running

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wajiha.platform.AppActions
import com.wajiha.state.DualScreenStore
import org.koin.compose.koinInject

/**
 * Running apps panel (secondary-screen mode): recent foreground apps with
 * bring-to-front on either display and kill actions.
 */
@Composable
fun RunningAppsPanel(modifier: Modifier = Modifier) {
    val store = koinInject<DualScreenStore>()
    val appActions = koinInject<AppActions>()
    val apps by store.runningApps.collectAsState()
    val secondaryDisplayId by store.secondaryDisplayId.collectAsState()

    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        Text(
            text = "Running apps",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(16.dp)
        )
        if (apps.isEmpty()) {
            Text(
                text = "Nothing recent — grant usage access in onboarding to enable this panel.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(apps, key = { it.packageName }) { app ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = app.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (app.isGame) {
                                Text(
                                    text = "Game",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        TextButton(onClick = { appActions.moveAppToDisplay(app.packageName, 0) }) {
                            Text("Top")
                        }
                        secondaryDisplayId?.let { displayId ->
                            TextButton(onClick = {
                                appActions.moveAppToDisplay(app.packageName, displayId)
                            }) {
                                Text("Bottom")
                            }
                        }
                        TextButton(onClick = { appActions.killApp(app.packageName) }) {
                            Text("Kill")
                        }
                    }
                }
            }
        }
    }
}
