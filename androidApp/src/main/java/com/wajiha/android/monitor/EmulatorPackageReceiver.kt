package com.wajiha.android.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Refreshes [ForegroundAppMonitor]'s known emulator set when packages are
 * installed, removed, or updated — faster than the 60s poll refresh alone.
 *
 * Safe for production: system [Intent.ACTION_PACKAGE_*] broadcasts are
 * exempt from implicit-broadcast restrictions and carry no Play policy risk.
 */
class EmulatorPackageReceiver : BroadcastReceiver(), KoinComponent {

    private val monitor: ForegroundAppMonitor by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_PACKAGE_ADDED &&
            action != Intent.ACTION_PACKAGE_REMOVED &&
            action != Intent.ACTION_PACKAGE_REPLACED
        ) {
            return
        }
        val packageName = intent.data?.schemeSpecificPart ?: return
        WajihaLog.d(WajihaTags.NOW_PLAYING, "packageChange: $action $packageName")
        val pending = goAsync()
        scope.launch {
            try {
                monitor.refreshKnownPackages()
            } finally {
                pending.finish()
            }
        }
    }
}
