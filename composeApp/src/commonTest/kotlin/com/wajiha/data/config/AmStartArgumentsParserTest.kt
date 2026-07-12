package com.wajiha.data.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AmStartArgumentsParserTest {
    @Test
    fun parsesDaijishouRetroArchCommand() {
        val parsed =
            AmStartArgumentsParser.parse(
                "-n com.retroarch.aarch64/com.retroarch.browser.retroactivity.RetroActivityFuture\n" +
                    " -e ROM {file.path}\n" +
                    " -e LIBRETRO opera\n" +
                    " -e CONFIGFILE /storage/emulated/0/Android/data/com.retroarch.aarch64/files/retroarch.cfg\n" +
                    " --activity-clear-top",
            )
        assertEquals("com.retroarch.aarch64", parsed.packageName)
        assertEquals("com.retroarch.browser.retroactivity.RetroActivityFuture", parsed.activityName)
        assertEquals(3, parsed.extras.size)
        assertEquals("{file.path}", parsed.extras.first { it.key == "ROM" }.value)
        assertEquals("opera", parsed.extras.first { it.key == "LIBRETRO" }.value)
        assertEquals(listOf("clear-top"), parsed.activityFlags)
    }

    @Test
    fun parsesIisuBareComponentWithDataUri() {
        val parsed =
            AmStartArgumentsParser.parse(
                "ru.vastness.altmer.real3doplayer/.EmulatorActivity -d %ROM_URI%",
            )
        assertEquals("ru.vastness.altmer.real3doplayer", parsed.packageName)
        assertEquals("ru.vastness.altmer.real3doplayer.EmulatorActivity", parsed.activityName)
        assertEquals("%ROM_URI%", parsed.dataUri)
    }

    @Test
    fun parsesQuotedValuesWithSpaces() {
        val parsed =
            AmStartArgumentsParser.parse(
                "com.seleuco.mame4d2024/com.seleuco.mame4droid.MAME4droid -a android.intent.action.VIEW " +
                    "-e cli_params -rompath '%GAMEDIRRAW%;%ROMPATHRAW%/adam' -cass1 '%ROMRAW%' -d adam",
            )
        assertEquals("com.seleuco.mame4d2024", parsed.packageName)
        assertEquals("android.intent.action.VIEW", parsed.action)
        val cli = parsed.extras.first { it.key == "cli_params" }
        assertTrue(cli.value.startsWith("-rompath"))
    }

    @Test
    fun parsesTypedExtras() {
        val parsed =
            AmStartArgumentsParser.parse(
                "-n a.b/.Main --ei slot 2 --ez fast true --el seed 42 --ef speed 1.5",
            )
        assertEquals("int", parsed.extras.first { it.key == "slot" }.type)
        assertEquals("boolean", parsed.extras.first { it.key == "fast" }.type)
        assertEquals("long", parsed.extras.first { it.key == "seed" }.type)
        assertEquals("float", parsed.extras.first { it.key == "speed" }.type)
    }

    @Test
    fun expandsRelativeActivityName() {
        val parsed = AmStartArgumentsParser.parse("-n com.dsemu.drastic/.DraSticActivity")
        assertEquals("com.dsemu.drastic.DraSticActivity", parsed.activityName)
    }

    @Test
    fun tokenizerHandlesNewlinesAndQuotes() {
        val tokens = AmStartArgumentsParser.tokenize("a\n b 'c d' \"e f\"")
        assertEquals(listOf("a", "b", "c d", "e f"), tokens)
    }
}
