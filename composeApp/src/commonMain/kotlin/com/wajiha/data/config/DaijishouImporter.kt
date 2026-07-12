package com.wajiha.data.config

import com.wajiha.data.WajihaJson
import kotlinx.serialization.Serializable

/**
 * Importer for Daijishō platform JSON files
 * (format: `{ databaseVersion, revisionNumber, platform: {...}, playerList: [...] }`).
 */
object DaijishouImporter {
    private val json = WajihaJson.Lenient

    @Serializable
    private data class DaijishouFile(
        val databaseVersion: Int = 0,
        val revisionNumber: Int = 0,
        val platform: DaijishouPlatform,
        val playerList: List<DaijishouPlayer> = emptyList(),
    )

    @Serializable
    private data class DaijishouPlatform(
        val name: String,
        val uniqueId: String,
        val shortname: String? = null,
        val description: String? = null,
        val acceptedFilenameRegex: String? = null,
        val scraperSourceList: List<String> = emptyList(),
        val boxArtAspectRatioId: Int = 0,
        val retroAchievementsConsoleIdList: List<Int> = emptyList(),
        val retroAchievementsAlias: String? = null,
    )

    @Serializable
    private data class DaijishouPlayer(
        val name: String,
        val uniqueId: String,
        val description: String? = null,
        val acceptedFilenameRegex: String? = null,
        val amStartArguments: String? = null,
        val killPackageProcesses: Boolean = false,
        val killPackageProcessesWarning: Boolean = false,
    )

    /** Daijishō boxArtAspectRatioId → W:H string (Daijishō UI order). */
    private val boxartRatios =
        mapOf(
            0 to "3:4",
            1 to "1:1",
            2 to "4:3",
            3 to "2:3",
            4 to "3:2",
            5 to "16:9",
        )

    fun import(jsonText: String): PlatformConfig {
        val file = json.decodeFromString<DaijishouFile>(jsonText)
        val platform = file.platform
        val emulators =
            file.playerList.mapIndexed { index, player ->
                toEmulatorConfig(player, isDefault = index == 0)
            }
        return PlatformConfig(
            id = platform.uniqueId,
            name = platform.name,
            shortName = platform.shortname ?: platform.uniqueId,
            extensions = extensionsFromPlayers(file.playerList),
            raConsoleId = platform.retroAchievementsConsoleIdList.firstOrNull(),
            libretroName =
                platform.scraperSourceList
                    .firstOrNull { it.startsWith("LIBRETRO:") }
                    ?.removePrefix("LIBRETRO:"),
            boxartAspectRatio = boxartRatios[platform.boxArtAspectRatioId],
            emulators = emulators,
        )
    }

    private fun toEmulatorConfig(
        player: DaijishouPlayer,
        isDefault: Boolean,
    ): EmulatorConfig {
        val args = player.amStartArguments.orEmpty()
        val parsed = AmStartArgumentsParser.parse(args)
        val romExtra = parsed.extras.firstOrNull { it.key == "ROM" }
        val libretro = parsed.extras.firstOrNull { it.key == "LIBRETRO" }?.value
        // Route type: {file.path} → real path; {file.uri} or -d data → uri
        val routeType =
            when {
                args.contains("{file.uri}") -> "uri"
                args.contains("{file.path}") -> "path"
                parsed.dataUri != null -> "uri"
                else -> "path"
            }
        return EmulatorConfig(
            id = player.uniqueId,
            name = player.name,
            packageNames = listOfNotNull(parsed.packageName),
            activityName = parsed.activityName,
            action = parsed.action,
            routeType = routeType,
            amStartArguments = player.amStartArguments,
            extras = parsed.extras.filterNot { it === romExtra },
            activityFlags = parsed.activityFlags,
            killBeforeLaunch = player.killPackageProcesses,
            libretroCore = libretro,
            isDefault = isDefault,
        )
    }

    /**
     * Union of extensions found in player `acceptedFilenameRegex` patterns of
     * the form `^(.*)\.(?:ext1|ext2)$`.
     */
    private val regexExtensionGroup = Regex("""\(\?:([a-zA-Z0-9|]+)\)""")

    private fun extensionsFromPlayers(players: List<DaijishouPlayer>): List<String> =
        players
            .mapNotNull { it.acceptedFilenameRegex }
            .flatMap { regex ->
                regexExtensionGroup.findAll(regex).flatMap { match ->
                    match.groupValues[1].split('|')
                }
            }.map { it.lowercase() }
            .filter { it.isNotEmpty() && it.length <= 8 }
            .distinct()
            .sorted()
}
