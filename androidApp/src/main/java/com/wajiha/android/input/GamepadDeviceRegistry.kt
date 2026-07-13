package com.wajiha.android.input

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import com.wajiha.input.ConnectedController
import com.wajiha.input.ControllerGlyphStore
import com.wajiha.input.GamepadDeviceClassifier
import com.wajiha.log.WajihaLog
import com.wajiha.log.WajihaLogKind

/**
 * Enumerates joystick/gamepad [InputDevice]s, classifies controller family, and
 * mirrors the list into [ControllerGlyphStore] for Auto glyph resolution.
 */
class GamepadDeviceRegistry(
    context: Context,
    private val glyphStore: ControllerGlyphStore,
) : InputManager.InputDeviceListener {
    private val inputManager =
        context.applicationContext.getSystemService(Context.INPUT_SERVICE) as InputManager

    fun start() {
        refresh()
        inputManager.registerInputDeviceListener(this, null)
        WajihaLog.d(WajihaLogKind.INPUT, "GamepadDeviceRegistry started")
    }

    fun stop() {
        inputManager.unregisterInputDeviceListener(this)
    }

    fun refresh() {
        val devices =
            InputDevice
                .getDeviceIds()
                .toList()
                .mapNotNull { id -> InputDevice.getDevice(id) }
                .filter { isGamepadOrJoystick(it) }
                .map { device ->
                    val name = device.name.orEmpty().ifBlank { "Controller ${device.id}" }
                    val vendorId = device.vendorId
                    val productId = device.productId
                    ConnectedController(
                        stableId = GamepadDeviceClassifier.stableId(name, vendorId, productId),
                        displayName = name,
                        type = GamepadDeviceClassifier.classify(name, vendorId),
                        deviceId = device.id,
                    )
                }.distinctBy { it.stableId }
        glyphStore.setConnectedDevices(devices)
        WajihaLog.d(
            WajihaLogKind.INPUT,
            "controllers: ${devices.joinToString { "${it.displayName}(${it.type})" }}",
        )
    }

    fun controllerForDeviceId(deviceId: Int): ConnectedController? =
        glyphStore.devices.value.firstOrNull { it.deviceId == deviceId }
            ?: run {
                val device = InputDevice.getDevice(deviceId) ?: return null
                if (!isGamepadOrJoystick(device)) return null
                val name = device.name.orEmpty().ifBlank { "Controller ${device.id}" }
                ConnectedController(
                    stableId =
                        GamepadDeviceClassifier.stableId(
                            name,
                            device.vendorId,
                            device.productId,
                        ),
                    displayName = name,
                    type = GamepadDeviceClassifier.classify(name, device.vendorId),
                    deviceId = device.id,
                )
            }

    override fun onInputDeviceAdded(deviceId: Int) = refresh()

    override fun onInputDeviceRemoved(deviceId: Int) = refresh()

    override fun onInputDeviceChanged(deviceId: Int) = refresh()

    companion object {
        fun isGamepadOrJoystick(device: InputDevice): Boolean {
            val sources = device.sources
            return (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK ||
                (sources and InputDevice.SOURCE_CLASS_JOYSTICK) != 0
        }
    }
}
