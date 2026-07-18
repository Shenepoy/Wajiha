package com.wajiha.data.db

/** Host-visible wipe entrypoint so androidApp need not compile against Room. */
fun interface DatabaseWiper {
    suspend fun wipeAllTables()
}
