package com.wajiha.ui.home

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibrarySelectionTest {
    @Test
    fun platformAndCollectionCannotBothBeSet() {
        val platform = LibrarySelection().selectPlatform("snes")
        val collection = platform.selectCollection(4L)

        assertNull(collection.platformId)
        assertEquals(4L, collection.collectionId)

        val backToPlatform = collection.selectPlatform("psx")
        assertEquals("psx", backToPlatform.platformId)
        assertNull(backToPlatform.collectionId)
    }

    @Test
    fun allAndCollectionsClearTheOtherFilter() {
        val collection = LibrarySelection().selectCollection(2L)
        val all = collection.selectAll()
        assertNull(all.platformId)
        assertNull(all.collectionId)

        val browsing = collection.openCollections()
        assertTrue(browsing.browsingCollections)
        assertNull(browsing.platformId)
        assertNull(browsing.collectionId)
    }

    @Test
    fun emptyCollectionYieldsNoGames() {
        val selection = LibrarySelection().selectCollection(9L)
        val games =
            gamesForLibrarySelection(
                selection = selection,
                all = listOf(1L, 2L),
                platform = listOf(1L),
                collection = emptyList(),
            )
        assertEquals(emptyList(), games)
    }

    @Test
    fun browsingCollectionsHidesTheGameGrid() {
        val selection = LibrarySelection().openCollections()
        val games =
            gamesForLibrarySelection(
                selection = selection,
                all = listOf(1L),
                platform = listOf(1L),
                collection = listOf(1L),
            )
        assertEquals(emptyList(), games)
    }
}
