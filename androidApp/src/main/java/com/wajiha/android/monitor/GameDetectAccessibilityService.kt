package com.wajiha.android.monitor

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Optional event-driven foreground detection (user opt-in via accessibility
 * settings). Reacts to window-state changes instantly instead of waiting for
 * the 2s UsageStats poll — same trade-off RetroHrai offers for hotkeys.
 */
class GameDetectAccessibilityService : AccessibilityService(), KoinComponent {

    private val monitor: ForegroundAppMonitor by inject()

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        monitor.onForegroundPackage(packageName)
    }

    override fun onInterrupt() = Unit
}
