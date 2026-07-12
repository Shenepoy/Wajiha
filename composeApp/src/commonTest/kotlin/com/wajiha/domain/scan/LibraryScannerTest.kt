package com.wajiha.domain.scan

import com.wajiha.platform.ScannedRom
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryScannerTest {
    private fun rom(
        name: String,
        parent: String = "dir1",
    ) = ScannedRom(uri = "content://x/$parent/$name", fileName = name, size = 1, lastModified = 0, parentId = parent)

    @Test
    fun hidesBinTracksWhenCuePresent() {
        val roms =
            listOf(
                rom("Game.cue"),
                rom("Game (Track 1).bin"),
                rom("Game (Track 2).bin"),
                rom("Other.sfc", parent = "dir2"),
            )
        val visible = LibraryScanner.filterMultiDiscTracks(roms).map { it.fileName }
        assertEquals(listOf("Game.cue", "Other.sfc"), visible)
    }

    @Test
    fun hidesDiscFilesWhenM3uPresent() {
        val roms =
            listOf(
                rom("Game.m3u"),
                rom("Game (Disc 1).chd"),
                rom("Game (Disc 2).chd"),
            )
        val visible = LibraryScanner.filterMultiDiscTracks(roms).map { it.fileName }
        assertEquals(listOf("Game.m3u"), visible)
    }

    @Test
    fun keepsStandaloneFilesInFoldersWithoutEntryFiles() {
        val roms = listOf(rom("Game.bin"), rom("Game2.iso"))
        val visible = LibraryScanner.filterMultiDiscTracks(roms).map { it.fileName }
        assertEquals(listOf("Game.bin", "Game2.iso"), visible)
    }

    @Test
    fun filtersFilesMatchingIgnorePatternsByFilename() {
        val roms =
            listOf(
                rom("Game.sfc"),
                rom("Game DLC.sfc"),
                rom("Expansion Pack.gba"),
            )
        val visible =
            LibraryScanner
                .filterIgnoredNamePatterns(roms, listOf("dlc", "expansion"))
                .map { it.fileName }
        assertEquals(listOf("Game.sfc"), visible)
    }

    @Test
    fun filtersFilesMatchingIgnorePatternsByUriPath() {
        val roms =
            listOf(
                rom("content.sfc", parent = "games"),
                rom("bonus.sfc", parent = "dlc-pack"),
            )
        val visible =
            LibraryScanner
                .filterIgnoredNamePatterns(roms, listOf("dlc"))
                .map { it.fileName }
        assertEquals(listOf("content.sfc"), visible)
    }

    @Test
    fun ignorePatternMatchIsCaseInsensitive() {
        val roms = listOf(rom("Super DEMO.sfc"), rom("Retail.sfc"))
        val visible =
            LibraryScanner
                .filterIgnoredNamePatterns(roms, listOf("demo"))
                .map { it.fileName }
        assertEquals(listOf("Retail.sfc"), visible)
    }

    @Test
    fun cleansDisplayNames() {
        assertEquals(
            "Chrono Trigger",
            LibraryScanner.cleanDisplayName("Chrono Trigger (USA) [!]"),
        )
        assertEquals(
            "Legend of Zelda, The - A Link to the Past",
            LibraryScanner.cleanDisplayName("Legend of Zelda, The - A Link to the Past (USA)"),
        )
        // Name entirely made of tags falls back to raw
        assertEquals("(USA)", LibraryScanner.cleanDisplayName("(USA)"))
    }
}
