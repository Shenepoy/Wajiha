package com.wajiha.data.config

import com.wajiha.data.db.EmulatorEntity
import com.wajiha.data.db.PlatformEntity
import com.wajiha.domain.repository.PlatformRepository
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import wajiha.composeapp.generated.resources.Res

/**
 * Installs the bundled starter platform/emulator set into the database and
 * imports user-provided Daijishō / iiSU config files.
 *
 * Bundled sources:
 * - 119 Daijishō platform JSONs (canonical: names, players, RA ids, boxart ratio)
 * - iiSU 173-console list (enrichment: extensions, RA ids, extra emulators)
 */
class ConfigInstaller(private val platformRepository: PlatformRepository) {

    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalResourceApi::class)
    suspend fun installBundledDefaults() {
        val index = json.decodeFromString<List<String>>(
            Res.readBytes("files/platforms/daijishou_index.json").decodeToString()
        )
        val daijishou = index.mapNotNull { fileName ->
            runCatching {
                DaijishouImporter.import(
                    Res.readBytes("files/platforms/daijishou/$fileName").decodeToString()
                )
            }.getOrNull()
        }
        val iisu = runCatching {
            IisuImporter.importAll(
                Res.readBytes("files/platforms/iisu_consoles.json").decodeToString()
            )
        }.getOrDefault(emptyList())

        install(merge(daijishou, iisu))
    }

    /** Import a user-picked Daijishō platform JSON. */
    suspend fun importDaijishouFile(jsonText: String): PlatformConfig {
        val config = DaijishouImporter.import(jsonText)
        install(listOf(config))
        return config
    }

    /** Import a user-picked iiSU consoles JSON. */
    suspend fun importIisuFile(jsonText: String): List<PlatformConfig> {
        val configs = IisuImporter.importAll(jsonText)
        install(configs)
        return configs
    }

    private fun merge(
        daijishou: List<PlatformConfig>,
        iisu: List<PlatformConfig>
    ): List<PlatformConfig> = ConfigMerger.merge(daijishou, iisu)

    suspend fun install(configs: List<PlatformConfig>) {
        val existingPlatforms = platformRepository.all().associateBy { it.id }
        val platformEntities = configs.map { config ->
            val existing = existingPlatforms[config.id]
            PlatformEntity(
                id = config.id,
                name = config.name,
                shortName = config.shortName,
                extensions = config.extensions.joinToString(","),
                raConsoleId = config.raConsoleId,
                screenScraperId = config.screenScraperId,
                libretroName = config.libretroName,
                boxartAspectRatio = config.boxartAspectRatio,
                sortIndex = config.sortIndex,
                // Preserve user state on re-install/upgrade
                enabled = existing?.enabled ?: true,
                defaultEmulatorId = existing?.defaultEmulatorId
                    ?: config.emulators.firstOrNull { it.isDefault }?.id
            )
        }
        val emulatorEntities = configs.flatMap { config ->
            config.emulators.map { emulator ->
                EmulatorEntity(
                    id = emulator.id,
                    platformId = config.id,
                    name = emulator.name,
                    packageNames = emulator.packageNames.joinToString(","),
                    activityName = emulator.activityName,
                    action = emulator.action,
                    routeType = emulator.routeType,
                    amStartArguments = emulator.amStartArguments,
                    extrasJson = emulator.extras.takeIf { it.isNotEmpty() }
                        ?.let { json.encodeToString(it) },
                    activityFlagsJson = emulator.activityFlags.takeIf { it.isNotEmpty() }
                        ?.let { json.encodeToString(it) },
                    keepSafUri = emulator.keepSafUri,
                    killBeforeLaunch = emulator.killBeforeLaunch,
                    libretroCore = emulator.libretroCore,
                    isDefault = emulator.isDefault
                )
            }
        }
        platformRepository.upsert(platformEntities)
        platformRepository.upsertEmulators(emulatorEntities)
    }

}

/**
 * Merge strategy: Daijishō is canonical for identity/names/players.
 * iiSU consoles matched by shortName contribute extension unions, RA id
 * fill-ins and additional emulators. Unmatched iiSU consoles are added.
 */
object ConfigMerger {

    /** Daijishō uniqueId → iiSU shortName where they differ. */
    private val aliases = mapOf(
        "megadrive" to "genesis",
        "pcengine" to "tg16",
        "gc" to "gamecube",
        "ss" to "saturn",
        "md" to "genesis"
    )

    fun merge(
        daijishou: List<PlatformConfig>,
        iisu: List<PlatformConfig>
    ): List<PlatformConfig> {
        val iisuById = iisu.associateBy { it.id }
        val usedIisu = mutableSetOf<String>()

        val merged = daijishou.mapIndexed { index, platform ->
            val match = iisuById[platform.id]
                ?: iisuById[platform.shortName]
                ?: iisuById[aliases[platform.id]]
            if (match != null) usedIisu += match.id
            if (match == null) {
                platform.copy(sortIndex = index)
            } else {
                val existingEmuIds = platform.emulators.map { it.id }.toSet()
                platform.copy(
                    sortIndex = index,
                    extensions = (platform.extensions + match.extensions).distinct().sorted(),
                    raConsoleId = platform.raConsoleId ?: match.raConsoleId,
                    emulators = platform.emulators +
                        match.emulators.filterNot { it.id in existingEmuIds }
                            .map { it.copy(isDefault = false) }
                )
            }
        }

        val extra = iisu.filterNot { it.id in usedIisu }
            .mapIndexed { i, platform -> platform.copy(sortIndex = merged.size + i) }
        return merged + extra
    }
}
