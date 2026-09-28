package com.wajiha.android.data

import android.content.Context
import android.content.Intent
import android.os.Process
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.work.WorkManager
import com.wajiha.android.MainActivity
import com.wajiha.data.db.DatabaseWiper
import com.wajiha.data.db.StoredDataCleaner
import com.wajiha.state.DualScreenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Drops the library index, preferences, and cached artwork, then starts a
 * fresh process so in-memory sessions cannot show the old library.
 */
class AndroidDatabaseWiper(
    private val context: Context,
    private val storedDataCleaner: StoredDataCleaner,
    private val dataStore: DataStore<Preferences>,
    private val dualScreenStore: DualScreenStore,
) : DatabaseWiper {
    override suspend fun wipeAllTables() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelUniqueWork(LIBRARY_SCAN_WORK)
        workManager.cancelUniqueWork(SCRAPE_WORK)
        dualScreenStore.activeSessions().map { it.packageName }.toList().forEach { packageName ->
            dualScreenStore.endGameSession(packageName)
        }
        storedDataCleaner.clear()
        dataStore.edit { it.clear() }
        withContext(Dispatchers.IO) {
            File(context.filesDir, "media").deleteRecursively()
            File(context.filesDir, "ra").deleteRecursively()
        }
        val relaunch =
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
        context.startActivity(relaunch)
        Process.killProcess(Process.myPid())
    }

    private companion object {
        private const val LIBRARY_SCAN_WORK = "wajiha-library-scan"
        private const val SCRAPE_WORK = "wajiha-scrape"
    }
}
