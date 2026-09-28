package com.wajiha.ui.home

/**
 * Library filter. A platform and a collection are never selected together.
 * Opening the collection list clears both.
 */
data class LibrarySelection(
    val platformId: String? = null,
    val collectionId: Long? = null,
    val browsingCollections: Boolean = false,
) {
    init {
        require(platformId == null || collectionId == null)
        require(!browsingCollections || (platformId == null && collectionId == null))
    }

    fun selectAll(): LibrarySelection = LibrarySelection()

    fun selectPlatform(id: String): LibrarySelection = LibrarySelection(platformId = id)

    fun openCollections(): LibrarySelection = LibrarySelection(browsingCollections = true)

    fun selectCollection(id: Long): LibrarySelection = LibrarySelection(collectionId = id)
}

enum class LibraryGameSource {
    All,
    Platform,
    Collection,
    None,
}

fun LibrarySelection.gameSource(): LibraryGameSource =
    when {
        browsingCollections -> LibraryGameSource.None
        collectionId != null -> LibraryGameSource.Collection
        platformId != null -> LibraryGameSource.Platform
        else -> LibraryGameSource.All
    }

/** Games shown for [selection]. An empty collection list stays empty. */
fun <T> gamesForLibrarySelection(
    selection: LibrarySelection,
    all: List<T>,
    platform: List<T>,
    collection: List<T>,
): List<T> =
    when (selection.gameSource()) {
        LibraryGameSource.All -> all
        LibraryGameSource.Platform -> platform
        LibraryGameSource.Collection -> collection
        LibraryGameSource.None -> emptyList()
    }

data class CollectionSummary(
    val id: Long,
    val name: String,
    val gameCount: Int,
)

fun collectionGameCountLabel(count: Int): String = if (count == 1) "1 game" else "$count games"
