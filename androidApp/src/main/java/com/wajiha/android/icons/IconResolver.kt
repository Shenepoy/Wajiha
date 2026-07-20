package com.wajiha.android.icons

import android.content.ComponentName
import android.content.Context
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.wajiha.data.prefs.IconAppearancePreferences
import com.wajiha.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

class IconResolver(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val cache: IconBitmapCache,
) {
    private var lastPackPackage: String? = null
    private var lastShapeId: String? = null

    data class Appearance(
        val packPackage: String,
        val shapeId: String,
    )

    suspend fun currentAppearance(): Appearance {
        val settings = settingsRepository.settings.first()
        var pack = IconAppearancePreferences.normalizePackPackage(settings.iconPackPackage)
        val shape = IconAppearancePreferences.normalizeShape(settings.iconShape)

        if (pack.isNotEmpty() && !IconPackDiscovery.isPackInstalled(context, pack)) {
            settingsRepository.setIconPackPackage("")
            cache.clear()
            CustomIconPackLoader.evict(pack)
            pack = ""
            lastPackPackage = ""
        }
        if ((lastPackPackage != null && lastPackPackage != pack) ||
            (lastShapeId != null && lastShapeId != shape)
        ) {
            cache.clear()
        }
        lastPackPackage = pack
        lastShapeId = shape
        return Appearance(pack, shape)
    }

    fun iconSizePx(): Int {
        val density = context.resources.displayMetrics.density
        return (density * 56f).roundToInt().coerceIn(72, 192)
    }

    fun resolveAppIcon(
        resolveInfo: ResolveInfo,
        activityName: String,
        packPackage: String,
        shapeId: String,
        sizePx: Int,
    ): ImageBitmap? {
        val packageName = resolveInfo.activityInfo.packageName
        val packVersion = IconPackDiscovery.packVersionCode(context, packPackage)
        val dpi = context.resources.displayMetrics.densityDpi
        val normalizedShape = IconAppearancePreferences.normalizeShape(shapeId)
        val calendarDay = CalendarIconSupport.cacheDayKey(packageName, activityName)

        cache
            .get(packPackage, packageName, activityName, dpi, packVersion, normalizedShape, calendarDay)
            ?.let { return it }

        val drawable = resolveDrawable(resolveInfo, packageName, activityName, packPackage) ?: return null
        val bitmap =
            try {
                AdaptiveIconRasterizer
                    .rasterize(drawable, sizePx, normalizedShape)
                    .asImageBitmap()
            } catch (_: Exception) {
                null
            } ?: return null

        cache.put(
            packPackage,
            packageName,
            activityName,
            dpi,
            packVersion,
            normalizedShape,
            calendarDay,
            bitmap,
        )
        return bitmap
    }

    private fun resolveDrawable(
        resolveInfo: ResolveInfo,
        packageName: String,
        activityName: String,
        packPackage: String,
    ): Drawable? {
        val pm = context.packageManager
        if (packPackage.isNotEmpty()) {
            val loader = CustomIconPackLoader.get(context, packPackage)
            if (loader != null) {
                val component =
                    if (activityName.isNotEmpty()) {
                        ComponentName(packageName, activityName)
                    } else {
                        ComponentName(packageName, resolveInfo.activityInfo.name)
                    }
                val drawableName = loader.drawableNameFor(component)
                if (drawableName != null) {
                    val fromPack = loader.loadDrawable(drawableName, context.resources.displayMetrics.densityDpi)
                    if (fromPack != null) return fromPack
                }
            }
        }
        return try {
            resolveInfo.loadIcon(pm)
        } catch (_: Exception) {
            null
        }
    }

    fun clearCache() {
        cache.clear()
        CustomIconPackLoader.clearAll()
        lastPackPackage = null
        lastShapeId = null
    }
}
