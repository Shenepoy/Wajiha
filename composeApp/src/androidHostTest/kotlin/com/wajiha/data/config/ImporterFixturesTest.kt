package com.wajiha.data.config

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Validates the importers against the bundled fixture corpus:
 * 119 Daijishō platform JSONs and the iiSU 173-console file.
 */
class ImporterFixturesTest {

    private val resourcesDir =
        File("src/commonMain/composeResources/files/platforms")

    @Test
    fun importsAllDaijishouFixtures() {
        val dir = resourcesDir.resolve("daijishou")
        assertTrue(dir.isDirectory, "fixture dir missing: $dir")
        val files = dir.listFiles { f -> f.extension == "json" }!!.sorted()
        assertEquals(119, files.size)

        val failures = mutableListOf<String>()
        for (file in files) {
            val result = runCatching { DaijishouImporter.import(file.readText()) }
            val config = result.getOrNull()
            when {
                result.isFailure -> failures += "${file.name}: ${result.exceptionOrNull()}"
                config!!.id.isBlank() -> failures += "${file.name}: blank id"
                config.name.isBlank() -> failures += "${file.name}: blank name"
            }
        }
        assertTrue(failures.isEmpty(), "Import failures:\n" + failures.joinToString("\n"))
    }

    @Test
    fun daijishouPlayersProduceLaunchableEmulators() {
        val dir = resourcesDir.resolve("daijishou")
        val files = dir.listFiles { f -> f.extension == "json" }!!
        var players = 0
        var withComponent = 0
        for (file in files) {
            val config = DaijishouImporter.import(file.readText())
            for (emulator in config.emulators) {
                players++
                if (emulator.packageNames.isNotEmpty() && emulator.activityName != null) {
                    withComponent++
                }
            }
        }
        assertTrue(players > 300, "expected several hundred players, got $players")
        // Every Daijishō player uses -n component syntax
        assertEquals(players, withComponent, "players missing component: ${players - withComponent}")
    }

    @Test
    fun importsIisuConsolesFixture() {
        val file = resourcesDir.resolve("iisu_consoles.json")
        assertTrue(file.isFile, "fixture missing: $file")
        val configs = IisuImporter.importAll(file.readText())
        assertEquals(173, configs.size)

        val threeDo = configs.first { it.id == "3do" }
        assertTrue("chd" in threeDo.extensions)
        assertEquals(43, threeDo.raConsoleId)
        assertTrue(threeDo.emulators.any { it.packageNames.contains("com.retroarch.aarch64") })
        assertTrue(threeDo.emulators.any { it.routeType == "uri" })

        // Every console has an id and a name
        assertTrue(configs.all { it.id.isNotBlank() && it.name.isNotBlank() })
    }

    @Test
    fun mergePrefersDaijishouAndAppendsIisuExtras() {
        val daijishou = listOf(
            PlatformConfig(
                id = "snes",
                name = "Super Nintendo",
                shortName = "snes",
                extensions = listOf("sfc", "smc"),
                raConsoleId = 3,
                emulators = listOf(
                    EmulatorConfig(id = "snes.ra64.snes9x", name = "RA snes9x", isDefault = true)
                )
            )
        )
        val iisu = listOf(
            PlatformConfig(
                id = "snes",
                name = "SNES (iiSU)",
                shortName = "snes",
                extensions = listOf("sfc", "zip"),
                emulators = listOf(
                    EmulatorConfig(id = "snes.snes9x-standalone", name = "Snes9x EX+", isDefault = true)
                )
            ),
            PlatformConfig(id = "pico8", name = "PICO-8", shortName = "pico8")
        )
        val merged = ConfigMerger.merge(daijishou, iisu)

        val snes = merged.first { it.id == "snes" }
        assertEquals("Super Nintendo", snes.name)
        assertEquals(listOf("sfc", "smc", "zip"), snes.extensions)
        assertEquals(2, snes.emulators.size)
        // iiSU emulator must not steal the default flag
        assertTrue(snes.emulators.first { it.id == "snes.snes9x-standalone" }.isDefault.not())
        assertTrue(merged.any { it.id == "pico8" })
    }
}
