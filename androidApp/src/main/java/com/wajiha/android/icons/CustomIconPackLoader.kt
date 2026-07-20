package com.wajiha.android.icons

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads an icon pack's appfilter mapping (Lawnchair CustomIconPack pattern).
 * Kept in-process per pack package name.
 */
class CustomIconPackLoader(
    private val context: Context,
    val packPackageName: String,
) {
    private val packResources: Resources =
        context.packageManager.getResourcesForApplication(packPackageName)
    private val componentMap = ConcurrentHashMap<ComponentName, String>()
    private val packageMap = ConcurrentHashMap<String, String>()
    private val idCache = ConcurrentHashMap<String, Int>()
    private var loaded = false

    @Synchronized
    fun ensureLoaded() {
        if (loaded) return
        loadAppFilter()
        loaded = true
    }

    fun drawableNameFor(componentName: ComponentName): String? {
        ensureLoaded()
        return componentMap[componentName]
            ?: packageMap[componentName.packageName]
    }

    fun loadDrawable(
        drawableName: String,
        iconDpi: Int,
    ): Drawable? {
        val id = drawableId(drawableName)
        if (id == 0) return null
        return try {
            packResources.getDrawableForDensity(id, iconDpi, null)
        } catch (_: Resources.NotFoundException) {
            null
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun drawableId(name: String): Int =
        idCache.getOrPut(name) {
            packResources.getIdentifier(name, "drawable", packPackageName)
        }

    private fun loadAppFilter() {
        val parser = openXml("appfilter") ?: return
        val compStart = "ComponentInfo{"
        val compEnd = "}"
        try {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                if (parser.name != "item") continue
                var component = parser.getAttributeValue(null, "component") ?: continue
                val drawable = parser.getAttributeValue(null, "drawable") ?: continue
                if (component.startsWith(compStart) && component.endsWith(compEnd)) {
                    component = component.substring(compStart.length, component.length - compEnd.length)
                }
                val parsed = ComponentName.unflattenFromString(component) ?: continue
                componentMap[parsed] = drawable
                packageMap.putIfAbsent(parsed.packageName, drawable)
            }
        } catch (_: Exception) {
            // Incomplete pack — fall back to system icons for unmapped apps.
        }
    }

    private fun openXml(name: String): XmlPullParser? =
        try {
            @SuppressLint("DiscouragedApi")
            val resourceId = packResources.getIdentifier(name, "xml", packPackageName)
            if (resourceId != 0) {
                context.packageManager.getXml(packPackageName, resourceId, null)
            } else {
                val factory = XmlPullParserFactory.newInstance()
                factory.newPullParser().also { parser ->
                    parser.setInput(packResources.assets.open("$name.xml"), Xml.Encoding.UTF_8.toString())
                }
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Exception) {
            null
        }

    companion object {
        private val loaders = ConcurrentHashMap<String, CustomIconPackLoader>()

        fun get(
            context: Context,
            packPackageName: String,
        ): CustomIconPackLoader? {
            if (packPackageName.isEmpty()) return null
            return try {
                loaders.getOrPut(packPackageName) {
                    CustomIconPackLoader(context.applicationContext, packPackageName)
                }
            } catch (_: PackageManager.NameNotFoundException) {
                loaders.remove(packPackageName)
                null
            }
        }

        fun evict(packPackageName: String) {
            loaders.remove(packPackageName)
        }

        fun clearAll() {
            loaders.clear()
        }
    }
}
