package com.wajiha.data.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Importer for iiSU `emuladores_default.json`
 * (format: `{ consoles: [ { shortName, longName, romExtensions, emulators: [...] } ] }`).
 */
object IisuImporter {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    private data class IisuFile(val consoles: List<IisuConsole> = emptyList())

    @Serializable
    private data class IisuConsole(
        val shortName: String,
        val longName: String,
        val releaseYear: String? = null,
        val manufacturer: String? = null,
        val retroAchievementsId: String? = null,
        val romExtensions: List<String> = emptyList(),
        val emulators: List<IisuEmulator> = emptyList()
    )

    @Serializable
    private data class IisuEmulator(
        val id: String,
        val name: String,
        val routeType: String = "uri",
        val commands: List<IisuCommand> = emptyList(),
        val packages: List<String> = emptyList()
    )

    @Serializable
    private data class IisuCommand(
        val description: String? = null,
        val command: String
    )

    fun importAll(jsonText: String): List<PlatformConfig> =
        json.decodeFromString<IisuFile>(jsonText).consoles.mapIndexed { index, console ->
            toPlatformConfig(console, index)
        }

    private fun toPlatformConfig(console: IisuConsole, sortIndex: Int): PlatformConfig {
        val emulators = console.emulators.flatMapIndexed { emuIndex: Int, emulator: IisuEmulator ->
            emulator.commands.mapIndexed { cmdIndex, command ->
                toEmulatorConfig(console.shortName, emulator, command, cmdIndex)
            }.ifEmpty { emptyList() }
                .mapIndexed { i, config ->
                    if (emuIndex == 0 && i == 0) config.copy(isDefault = true) else config
                }
        }
        return PlatformConfig(
            id = console.shortName,
            name = console.longName,
            shortName = console.shortName,
            extensions = console.romExtensions
                .map { it.removePrefix(".").lowercase() }
                .distinct()
                .sorted(),
            raConsoleId = console.retroAchievementsId?.toIntOrNull(),
            sortIndex = sortIndex,
            emulators = emulators
        )
    }

    private fun toEmulatorConfig(
        consoleId: String,
        emulator: IisuEmulator,
        command: IisuCommand,
        commandIndex: Int
    ): EmulatorConfig {
        // %PACKAGE% in commands means "substitute per candidate package"; keep
        // the first package for component resolution, launcher retries others.
        val expanded = command.command.replace(
            "%PACKAGE%",
            emulator.packages.firstOrNull() ?: "%PACKAGE%"
        )
        val parsed = AmStartArgumentsParser.parse(expanded)
        val libretro = parsed.extras.firstOrNull { it.key == "LIBRETRO" }?.value
            ?.removeSuffix("_libretro_android.so")
        val idSuffix = if (commandIndex > 0) ".$commandIndex" else ""
        val packages = emulator.packages.ifEmpty { listOfNotNull(parsed.packageName) }
        return EmulatorConfig(
            id = "$consoleId.${emulator.id.lowercase()}$idSuffix",
            name = command.description ?: emulator.name,
            packageNames = packages,
            activityName = parsed.activityName,
            action = parsed.action,
            routeType = emulator.routeType,
            amStartArguments = command.command,
            extras = parsed.extras.filterNot { it.key == "ROM" },
            activityFlags = parsed.activityFlags,
            libretroCore = libretro
        )
    }
}
