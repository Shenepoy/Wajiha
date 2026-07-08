package com.wajiha.android.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomPathMatcherTest {

    @Test
    fun normalizePath_collapsesSdcardSymlink() {
        val normalized = RomPathMatcher.normalizePath("/sdcard/ROMs/game.iso")
        assertTrue(normalized.endsWith("/ROMs/game.iso"))
        assertFalse(normalized.contains("/sdcard/"))
    }

    @Test
    fun normalizePath_stripsFileScheme() {
        val normalized = RomPathMatcher.normalizePath("file:///storage/emulated/0/ROMs/game.chd")
        assertEquals("/storage/emulated/0/ROMs/game.chd", normalized)
    }

    @Test
    fun fileNameFromPath_returnsBasename() {
        assertEquals(
            "Chrono Trigger (USA).sfc",
            RomPathMatcher.fileNameFromPath("/storage/emulated/0/ROMs/Chrono Trigger (USA).sfc")
        )
    }

    @Test
    fun looksLikeSerial_matchesPs2Codes() {
        assertTrue(RomPathMatcher.looksLikeSerial("SCUS-97501"))
        assertFalse(RomPathMatcher.looksLikeSerial("game.iso"))
    }
}
