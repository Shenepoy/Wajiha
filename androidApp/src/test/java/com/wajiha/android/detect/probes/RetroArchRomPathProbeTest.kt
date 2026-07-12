package com.wajiha.android.detect.probes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RetroArchRomPathProbeTest {

    @Test
    fun parseCfgValue_readsQuotedPaths() {
        val cfg = """
            playlist_directory = "/storage/emulated/0/RetroArch/playlists"
            content_history_path = "/storage/emulated/0/RetroArch/playlists/builtin/content_history.lpl"
        """.trimIndent()
        assertEquals(
            "/storage/emulated/0/RetroArch/playlists/builtin/content_history.lpl",
            IniKeyParser.cfgValue(cfg, "content_history_path")
        )
    }

    @Test
    fun parseHistoryContent_picksNewestJsonItem() {
        val fixture = javaClass.classLoader!!
            .getResourceAsStream("detect/retroarch/content_history.lpl")!!
            .bufferedReader()
            .readText()
        val candidate = RetroArchRomPathProbe.parseHistoryContent(
            fixture,
            sessionStartedAt = 1_780_000_000_000L
        )
        assertNotNull(candidate)
        assertEquals("/storage/emulated/0/ROMs/SNES/Chrono Trigger (USA).sfc", candidate!!.rawPath)
        assertEquals("snes", candidate.platformHint)
    }

    @Test
    fun platformFromCore_mapsSnes9x() {
        assertEquals("snes", RetroArchRomPathProbe.platformFromCore("snes9x_libretro"))
    }

    @Test
    fun parsePlaytimeDat_picksLatestSerial() {
        val content = """
            SCUS-97501                       76268                1782356220
            SLUS-21447                       1655                 1783530905
        """.trimIndent()
        val parsed = AetherSx2RomPathProbe.parsePlaytimeDat(content)
        assertNotNull(parsed)
        assertEquals("SLUS-21447", parsed!!.first)
        assertEquals(1_783_530_905L, parsed.second)
    }

    @Test
    fun serialFromSaveStateName_extractsCode() {
        assertEquals(
            "SCUS-97501",
            AetherSx2RomPathProbe.serialFromSaveStateName("SCUS-97501 (7571AAEE).00.p2s")
        )
        assertNull(AetherSx2RomPathProbe.serialFromSaveStateName("autosave.p2s"))
    }

    @Test
    fun extractPathFromIni_readsFilenameKey() {
        val ini = """
            [UI]
            Filename = /storage/emulated/0/ROMs/PS2/Game.iso
        """.trimIndent()
        assertEquals(
            "/storage/emulated/0/ROMs/PS2/Game.iso",
            AetherSx2RomPathProbe.extractPathFromIni(ini)
        )
    }
}
