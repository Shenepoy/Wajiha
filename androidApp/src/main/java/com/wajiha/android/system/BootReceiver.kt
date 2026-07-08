package com.wajiha.android.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * On boot: nothing heavy — Android relaunches the HOME activity itself when
 * Wajiha is the default launcher. We only warm the process so the first
 * frame is fast (Application.onCreate runs Koin + DB init).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }
        // Process is now warm; Koin/database were initialized by Application.
    }
}
