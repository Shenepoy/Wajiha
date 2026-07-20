package com.wajiha.android.platform

import android.content.Context
import com.wajiha.android.library.RomFolderManager
import com.wajiha.android.work.ScrapeWorker
import com.wajiha.data.scraper.BatchScraper
import com.wajiha.data.scraper.ScrapeRunPolicy
import com.wajiha.platform.LibraryActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Bridges shared UI to the host activity's SAF folder picker. The current
 * foreground activity registers itself as the pick handler.
 */
class AndroidLibraryActions(
    private val context: Context,
    private val romFolderManager: RomFolderManager,
    private val batchScraper: BatchScraper,
) : LibraryActions {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Set by the host activity; invoked with the platformId to pick for. */
    var folderPickHandler: ((String) -> Unit)? = null

    override fun pickRomFolder(platformId: String) {
        folderPickHandler?.invoke(platformId)
    }

    override fun rescanLibrary() = romFolderManager.rescanAll()

    override fun rescanPlatform(platformId: String) = romFolderManager.rescanPlatform(platformId)

    override suspend fun startScrape(
        platformId: String?,
        mode: String,
    ): String? {
        val policy = ScrapeRunPolicy.fromName(mode)
        val blocked = batchScraper.preflightMessage(platformId, policy)
        if (blocked != null) return blocked
        if (ScrapeWorker.isWorkActive(context)) {
            return "A scrape is already running"
        }
        ScrapeWorker.enqueue(context, platformId, policy)
        return null
    }

    override suspend fun retryFailedScrape(platformId: String?): String? {
        if (batchScraper.progress.value.running || ScrapeWorker.isWorkActive(context)) {
            return "A scrape is already running"
        }
        if (!batchScraper.hasRetryableIssues(platformId)) {
            return "Nothing to retry"
        }
        batchScraper.prepareRetryFailed(platformId)
        ScrapeWorker.enqueue(context, platformId, ScrapeRunPolicy.Force)
        return null
    }

    override fun cancelScrape() = ScrapeWorker.cancel(context)
}
