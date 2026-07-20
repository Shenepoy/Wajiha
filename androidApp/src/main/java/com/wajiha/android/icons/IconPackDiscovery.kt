package com.wajiha.android.icons

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.wajiha.platform.IconPackInfo

/** Discovers Nova/ADW/Atom/Apex-style icon packs (Lawnchair discovery intents). */
object IconPackDiscovery {
    private val iconPackIntents =
        listOf(
            Intent("com.novalauncher.THEME"),
            Intent("org.adw.launcher.icons.ACTION_PICK_ICON"),
            Intent("com.dlto.atom.launcher.THEME"),
            Intent(Intent.ACTION_MAIN).addCategory("com.anddoes.launcher.THEME"),
        )

    fun listPacks(
        context: Context,
        iconSizePx: Int,
    ): List<IconPackInfo> {
        val pm = context.packageManager
        val packs =
            iconPackIntents
                .flatMap { intent ->
                    pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                }.associateBy { it.activityInfo.packageName }
                .map { (_, info) ->
                    val packageName = info.activityInfo.packageName
                    val label = info.loadLabel(pm).toString()
                    val icon =
                        try {
                            info
                                .loadIcon(pm)
                                .toBitmap(iconSizePx, iconSizePx)
                                .asImageBitmap()
                        } catch (_: Exception) {
                            null
                        }
                    IconPackInfo(packageName, label, icon)
                }.sortedBy { it.label.lowercase() }

        val systemIcon =
            try {
                context.packageManager
                    .getApplicationIcon(context.packageName)
                    .toBitmap(iconSizePx, iconSizePx)
                    .asImageBitmap()
            } catch (_: Exception) {
                null
            }
        return listOf(IconPackInfo("", "System icons", systemIcon)) + packs
    }

    fun isPackInstalled(
        context: Context,
        packageName: String,
    ): Boolean {
        if (packageName.isEmpty()) return true
        return try {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun packVersionCode(
        context: Context,
        packageName: String,
    ): Long {
        if (packageName.isEmpty()) return 0L
        return try {
            @Suppress("DEPRECATION")
            context.packageManager
                .getPackageInfo(packageName, 0)
                .longVersionCode
        } catch (_: Exception) {
            0L
        }
    }
}
