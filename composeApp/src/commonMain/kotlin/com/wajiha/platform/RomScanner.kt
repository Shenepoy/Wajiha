package com.wajiha.platform

/** A candidate ROM file found while walking a SAF tree. */
data class ScannedRom(
    val uri: String,
    val fileName: String,
    val size: Long,
    val lastModified: Long,
    /** Document id of the parent directory, used for sibling grouping */
    val parentId: String,
)

/** Platform-specific SAF tree walker (actual: DocumentsContract on Android). */
interface RomScanner {
    suspend fun scan(
        treeUri: String,
        extensions: Set<String>,
        maxDepth: Int,
    ): List<ScannedRom>
}

/** Streaming hash computation over a content uri (actual: ContentResolver). */
interface RomHasher {
    /** CRC32 of the file, hex lowercase, or null on failure/size cap. */
    suspend fun crc32(
        uri: String,
        maxBytes: Long,
    ): String?

    /** MD5 of the file, hex lowercase, or null on failure/size cap. */
    suspend fun md5(
        uri: String,
        maxBytes: Long,
    ): String?
}
