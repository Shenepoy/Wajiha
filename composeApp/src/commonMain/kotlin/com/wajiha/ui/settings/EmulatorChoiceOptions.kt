package com.wajiha.ui.settings

import com.wajiha.data.db.EmulatorEntity
import com.wajiha.ui.components.gamepad.MultiChoiceOption

fun EmulatorEntity.packageNameCandidates(): List<String> = packageNames.split(',').map { it.trim() }.filter { it.isNotEmpty() }

fun EmulatorEntity.isInstalledOnDevice(isPackageInstalled: (String) -> Boolean): Boolean = packageNameCandidates().any(isPackageInstalled)

private fun emulatorChoiceDescription(
    emulator: EmulatorEntity,
    installed: Boolean,
): String {
    if (!installed) return "Not installed"
    return buildString {
        emulator.packageNameCandidates().firstOrNull()?.let { append(it) }
        emulator.libretroCore?.let { core ->
            if (isNotEmpty()) append(" · ")
            append(core)
        }
    }.ifBlank { "Installed" }
}

fun List<EmulatorEntity>.toEmulatorChoiceOptions(
    isPackageInstalled: (String) -> Boolean,
    includePlatformDefault: Boolean = false,
): List<MultiChoiceOption> =
    buildList {
        if (includePlatformDefault) {
            add(
                MultiChoiceOption(
                    value = "",
                    label = "Platform default",
                    description = "Use the platform's configured emulator",
                ),
            )
        }
        val sortedEmulators =
            sortedWith(
                compareByDescending<EmulatorEntity> { it.isInstalledOnDevice(isPackageInstalled) }
                    .thenBy { it.name.lowercase() },
            )
        for (emulator in sortedEmulators) {
            val installed = emulator.isInstalledOnDevice(isPackageInstalled)
            add(
                MultiChoiceOption(
                    value = emulator.id,
                    label = emulator.name,
                    description = emulatorChoiceDescription(emulator, installed),
                    enabled = installed,
                ),
            )
        }
    }
