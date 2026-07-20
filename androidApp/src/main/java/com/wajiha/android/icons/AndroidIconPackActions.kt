package com.wajiha.android.icons

import android.content.Context
import com.wajiha.platform.IconPackActions
import com.wajiha.platform.IconPackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidIconPackActions(
    private val context: Context,
    private val iconResolver: IconResolver,
) : IconPackActions {
    override suspend fun installedPacks(): List<IconPackInfo> =
        withContext(Dispatchers.IO) {
            IconPackDiscovery.listPacks(context, iconResolver.iconSizePx())
        }
}
