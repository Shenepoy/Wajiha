package com.wajiha.android.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/**
 * Deletes a ROM referenced by a game [uri] (content:// SAF or file://).
 * Returns null on success, otherwise a user-displayable error.
 */
class RomFileDeleter(
    private val context: Context,
) {
    fun delete(uri: String): String? =
        try {
            when {
                uri.startsWith("content://") -> deleteSaf(Uri.parse(uri))
                uri.startsWith("file://") -> deleteFile(Uri.parse(uri).path ?: return "Invalid file URI")
                else -> "Unsupported URI scheme"
            }
        } catch (e: Exception) {
            e.message ?: "Delete failed"
        }

    private fun deleteSaf(uri: Uri): String? =
        if (DocumentsContract.isDocumentUri(context, uri)) {
            val deleted = DocumentsContract.deleteDocument(context.contentResolver, uri)
            if (deleted) null else "Could not delete file (permission denied?)"
        } else {
            "Not a document URI"
        }

    private fun deleteFile(path: String): String? {
        val file = File(path)
        return if (file.exists() && file.delete()) null else "Could not delete file"
    }
}
