package com.wajiha.android.launch

/** Fully resolved emulator launch request. */
data class LaunchSpec(
    val packageName: String,
    val activityName: String?,
    val action: String?,
    val category: String? = null,
    /**
     * Data value. May carry Wajiha markers:
     * - `wajiha-realpath:<content-uri>` → resolve to a real filesystem path
     * - `wajiha-localuri:<content-uri>` → keep content:// for permission grants
     */
    val data: String? = null,
    val mimeType: String? = null,
    val extras: List<LaunchExtra> = emptyList(),
    val activityFlags: List<String> = emptyList(),
    val keepSafUri: Boolean = false,
    val killBeforeLaunch: Boolean = false
)

data class LaunchExtra(
    val key: String,
    val value: String,
    /** string | bool | boolean | int | long | float | uri | string_array */
    val type: String = "string"
)

sealed interface LaunchResult {
    data object Success : LaunchResult
    data class EmulatorNotInstalled(val packageName: String) : LaunchResult
    data class ActivityNotFound(val packageName: String) : LaunchResult
    data class PermissionDenied(val message: String?) : LaunchResult
    data class Failed(val message: String?) : LaunchResult
}
