package com.wajiha.platform

import kotlinx.coroutines.flow.StateFlow

data class SystemStatus(
    val batteryPercent: Int = -1,
    val charging: Boolean = false,
    /** 0..1, -1 when unknown */
    val brightness: Float = -1f,
    /** 0..1 media volume */
    val volume: Float = 0f,
    val screenTimeoutSec: Int = 0,
    val wifiEnabled: Boolean = false,
    val bluetoothEnabled: Boolean = false,
    val torchOn: Boolean = false
)

/** Which permissions/roles the onboarding wizard walks through. */
data class PermissionStates(
    val usageAccess: Boolean = false,
    val writeSettings: Boolean = false,
    val notifications: Boolean = false,
    val allFilesAccess: Boolean = false,
    val isDefaultLauncher: Boolean = false
)

/** Host-side system control surface (quick settings, launcher helpers). */
interface SystemControls {
    val status: StateFlow<SystemStatus>

    fun refreshStatus()

    /** Needs WRITE_SETTINGS; returns false when not granted. */
    fun setBrightness(fraction: Float): Boolean
    fun setVolume(fraction: Float)
    fun setScreenTimeout(seconds: Int): Boolean
    fun toggleTorch()

    fun openWifiSettings()
    fun openBluetoothSettings()

    // Permissions / roles
    fun permissionStates(): PermissionStates
    fun requestUsageAccess()
    fun requestWriteSettings()
    fun requestNotifications()
    fun requestAllFilesAccess()
    fun openHomeSettings()
    fun openAppInfo(packageName: String)
}
