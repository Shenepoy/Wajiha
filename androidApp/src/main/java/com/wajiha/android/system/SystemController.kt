package com.wajiha.android.system

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import com.wajiha.platform.PermissionStates
import com.wajiha.platform.SystemControls
import com.wajiha.platform.SystemStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android implementation of the quick-settings surface: brightness
 * (WRITE_SETTINGS), media volume, battery, screen timeout, torch, and the
 * permission/role helpers used by onboarding.
 */
class SystemController(private val context: Context) : SystemControls {

    private val _status = MutableStateFlow(SystemStatus())
    override val status: StateFlow<SystemStatus> = _status.asStateFlow()

    private val audioManager get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val cameraManager get() = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private var torchOn = false
    private val torchCameraId: String? by lazy {
        try {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun refreshStatus() {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val chargeStatus = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

        val brightness = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (_: Exception) {
            -1f
        }
        val timeoutMs = try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
        } catch (_: Exception) {
            0
        }

        val wifi = try {
            (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
        } catch (_: Exception) {
            false
        }
        val bt = try {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
                .adapter?.isEnabled == true
        } catch (_: Exception) {
            false
        }

        _status.value = SystemStatus(
            batteryPercent = if (level >= 0) level * 100 / scale else -1,
            charging = chargeStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                chargeStatus == BatteryManager.BATTERY_STATUS_FULL,
            brightness = brightness,
            volume = if (maxVolume > 0) volume.toFloat() / maxVolume else 0f,
            screenTimeoutSec = timeoutMs / 1000,
            wifiEnabled = wifi,
            bluetoothEnabled = bt,
            torchOn = torchOn
        )
    }

    override fun setBrightness(fraction: Float): Boolean {
        if (!Settings.System.canWrite(context)) return false
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                (fraction.coerceIn(0f, 1f) * 255).toInt()
            )
            _status.value = _status.value.copy(brightness = fraction)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun setVolume(fraction: Float) {
        try {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                (fraction.coerceIn(0f, 1f) * max).toInt(),
                0
            )
            _status.value = _status.value.copy(volume = fraction)
        } catch (_: Exception) {
        }
    }

    override fun setScreenTimeout(seconds: Int): Boolean {
        if (!Settings.System.canWrite(context)) return false
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                seconds * 1000
            )
            _status.value = _status.value.copy(screenTimeoutSec = seconds)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun toggleTorch() {
        val id = torchCameraId ?: return
        try {
            torchOn = !torchOn
            cameraManager.setTorchMode(id, torchOn)
            _status.value = _status.value.copy(torchOn = torchOn)
        } catch (_: Exception) {
            torchOn = false
        }
    }

    override fun openWifiSettings() = startSettings(Settings.ACTION_WIFI_SETTINGS)
    override fun openBluetoothSettings() = startSettings(Settings.ACTION_BLUETOOTH_SETTINGS)

    // ---- permissions / roles ----

    override fun permissionStates(): PermissionStates {
        val notifications = if (Build.VERSION.SDK_INT >= 33) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .areNotificationsEnabled()
        }
        return PermissionStates(
            usageAccess = hasUsageAccess(),
            writeSettings = Settings.System.canWrite(context),
            notifications = notifications,
            allFilesAccess = Environment.isExternalStorageManager(),
            isDefaultLauncher = isDefaultLauncher()
        )
    }

    fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(intent, 0)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        return appOps.unsafeCheckOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        ) == android.app.AppOpsManager.MODE_ALLOWED
    }

    override fun requestUsageAccess() = startSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    override fun requestWriteSettings() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                .setData(Uri.parse("package:${context.packageName}"))
        )
    }

    /** Set by the host activity to route runtime permission requests. */
    var notificationPermissionHandler: (() -> Unit)? = null

    override fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionHandler?.invoke()
        } else {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
    }

    override fun requestAllFilesAccess() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                .setData(Uri.parse("package:${context.packageName}"))
        )
    }

    override fun openHomeSettings() = startSettings(Settings.ACTION_HOME_SETTINGS)

    override fun openAppInfo(packageName: String) {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName"))
        )
    }

    override fun isPackageInstalled(packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: Exception) {
        false
    }

    override fun appVersionLabel(): String {
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            null
        }
        return if (version.isNullOrBlank()) "Wajiha" else "Wajiha $version"
    }

    private fun startSettings(action: String) = startActivity(Intent(action))

    private fun startActivity(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }
}
