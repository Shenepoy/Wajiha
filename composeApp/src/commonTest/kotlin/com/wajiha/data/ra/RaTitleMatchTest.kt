package com.wajiha.data.ra

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RaTitleMatchTest {
    private val games =
        listOf(
            RaListedGame(2688, "Ape Escape 2"),
            RaListedGame(2689, "Ape Escape 3"),
            RaListedGame(100, "~Hack~ Ape Escape 3 Bonus"),
        )

    @Test
    fun matchesUsaChdFileName() {
        assertEquals(
            2689L,
            matchRaGameIdByTitle(games, "Ape Escape 3 (USA).chd"),
        )
    }

    @Test
    fun matchesPlainTitle() {
        assertEquals(2689L, matchRaGameIdByTitle(games, "Ape Escape 3"))
    }

    @Test
    fun prefersExactOverHack() {
        assertEquals(2689L, matchRaGameIdByTitle(games, "Ape Escape 3"))
    }

    @Test
    fun ambiguousReturnsNull() {
        assertNull(matchRaGameIdByTitle(games, "Ape Escape"))
    }
}
