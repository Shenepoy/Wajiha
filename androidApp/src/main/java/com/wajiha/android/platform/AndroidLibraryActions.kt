package com.wajiha.android.platform

import android.content.Context
import com.wajiha.android.library.RomFolderManager
import com.wajiha.android.work.ScrapeWorker
import com.wajiha.platform.LibraryActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Bridges shared UI to the host activity's SAF folder picker. The current
 * foreground activity registers itself as the pick handler.
 */
class AndroidLibraryActions(
    private val context: Context,
    private val romFolderManager: RomFolderManager
) : LibraryActions {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Set by the host activity; invoked with the platformId to pick for. */
    var folderPickHandler: ((String) -> Unit)? = null

    override fun pickRomFolder(platformId: String) {
        folderPickHandler?.invoke(platformId)
    }

    override fun rescanLibrary() = romFolderManager.rescanAll()

    override fun rescanPlatform(platformId: String) = romFolderManager.rescanPlatform(platformId)

    override fun startScrape(platformId: String?) {
        scope.launch { ScrapeWorker.enqueue(context, platformId) }
    }

    override fun cancelScrape() = ScrapeWorker.cancel(context)
}
