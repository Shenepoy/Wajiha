package com.wajiha.platform

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Walks a persisted SAF tree with DocumentsContract child queries (much
 * faster than DocumentFile). Depth-limited, extension-filtered.
 */
class SafRomScanner(
    private val context: Context,
) : RomScanner {
    override suspend fun scan(
        treeUri: String,
        extensions: Set<String>,
        maxDepth: Int,
    ): List<ScannedRom> =
        withContext(Dispatchers.IO) {
            val tree = Uri.parse(treeUri)
            val rootDocId = DocumentsContract.getTreeDocumentId(tree)
            val results = mutableListOf<ScannedRom>()
            walk(tree, rootDocId, extensions, maxDepth, 0, results)
            results
        }

    private fun walk(
        tree: Uri,
        parentDocId: String,
        extensions: Set<String>,
        maxDepth: Int,
        depth: Int,
        out: MutableList<ScannedRom>,
    ) {
        if (depth > maxDepth) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId)
        val projection =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            )
        val cursor =
            try {
                context.contentResolver.query(childrenUri, projection, null, null, null)
            } catch (_: Exception) {
                null
            } ?: return

        cursor.use { c ->
            while (c.moveToNext()) {
                val docId = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (!name.startsWith(".")) {
                        walk(tree, docId, extensions, maxDepth, depth + 1, out)
                    }
                    continue
                }
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext.isEmpty() || ext !in extensions) continue
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
                out +=
                    ScannedRom(
                        uri = uri.toString(),
                        fileName = name,
                        size = if (c.isNull(3)) 0 else c.getLong(3),
                        lastModified = if (c.isNull(4)) 0 else c.getLong(4),
                        parentId = parentDocId,
                    )
            }
        }
    }
}
