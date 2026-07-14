package com.wajiha.android.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.wajiha.android.work.LibraryScanWorker
import com.wajiha.data.db.RomFolderEntity
import com.wajiha.domain.repository.GameRepository
import com.wajiha.domain.repository.PlatformRepository
import com.wajiha.domain.scan.ScanDepth

/**
 * Persists SAF tree picks as ROM folders. Use with
 * `ActivityResultContracts.OpenDocumentTree` from settings/onboarding UI:
 *
 * ```
 * val pickFolder = rememberLauncherForActivityResult(OpenDocumentTree()) { uri ->
 *     uri?.let { scope.launch { romFolderManager.addFolder(it, platformId) } }
 * }
 * ```
 */
class RomFolderManager(
    private val context: Context,
    private val gameRepository: GameRepository,
    private val platformRepository: PlatformRepository,
) {
    suspend fun addFolder(
        treeUri: Uri,
        platformId: String,
        scanDepth: Int? = null,
    ): Long {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        val depth =
            scanDepth
                ?: ScanDepth.forDeepScan(platformRepository.byId(platformId)?.deepScan == true)
        val id =
            gameRepository.addRomFolder(
                RomFolderEntity(
                    platformId = platformId,
                    treeUri = treeUri.toString(),
                    scanDepth = depth,
                ),
            )
        LibraryScanWorker.enqueue(context, platformId = platformId)
        return id
    }

    suspend fun removeFolder(folderId: Long) {
        gameRepository.removeRomFolder(folderId)
    }

    fun rescanAll() {
        LibraryScanWorker.enqueue(context)
    }

    fun rescanPlatform(platformId: String) {
        LibraryScanWorker.enqueue(context, platformId = platformId)
    }
}
