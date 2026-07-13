package com.wajiha.input

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Connected controllers + last-input sticky type for Auto glyph resolution.
 * Android [com.wajiha.android.input.GamepadDeviceRegistry] feeds device lists;
 * key/trigger handlers call [noteInput] on meaningful edges.
 */
class ControllerGlyphStore {
    private val _devices = MutableStateFlow<List<ConnectedController>>(emptyList())
    val devices: StateFlow<List<ConnectedController>> = _devices.asStateFlow()

    private val _lastInputType = MutableStateFlow(ControllerDeviceType.Xbox)
    val lastInputType: StateFlow<ControllerDeviceType> = _lastInputType.asStateFlow()

    private val _lastInputStableId = MutableStateFlow<String?>(null)
    val lastInputStableId: StateFlow<String?> = _lastInputStableId.asStateFlow()

    fun setConnectedDevices(devices: List<ConnectedController>) {
        _devices.value = devices
    }

    /** Record a gamepad key / trigger edge for Auto glyphs (always updates sticky type). */
    fun noteInput(
        stableId: String?,
        type: ControllerDeviceType,
    ) {
        _lastInputType.value = type
        _lastInputStableId.value = stableId
    }

    fun noteInputByDeviceId(deviceId: Int) {
        val device = _devices.value.firstOrNull { it.deviceId == deviceId } ?: return
        noteInput(device.stableId, device.type)
    }

    fun effectiveScheme(
        glyphsEnabled: Boolean,
        schemePref: String,
    ): ControllerGlyphScheme =
        ControllerGlyphLabels.resolveEffectiveScheme(
            glyphsEnabled = glyphsEnabled,
            schemePref = schemePref,
            lastInputType = _lastInputType.value,
        )
}
