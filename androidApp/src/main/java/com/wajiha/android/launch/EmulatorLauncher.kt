package com.wajiha.android.launch

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StrictMode
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File

/**
 * Launches external emulators with correct SAF permission handling.
 * Port of NeoStation's EmulatorLauncher (see Study/docs/06-emulator-launch-patterns.md):
 *
 * - synchronous grantUriPermission + ClipData before startActivity
 * - FileProvider rewrap for single-file ROMs (keeps .emu-series save states
 *   out of the ROM dir), skipped for multi-file formats and keep_saf_uri
 * - sibling-track grants for .cue/.gdi/.m3u
 * - parent-tree + prefix grants; zip sidecar folder grants
 * - RetroArch LIBRETRO core path expansion + CONFIGFILE default
 */
object EmulatorLauncher {

    private const val ROM_IMPORT_DIR = "rom_import"
    private const val MAX_CACHE_AGE_MS = 7 * 24 * 60 * 60 * 1000L
    private const val MAX_ROM_CACHE_SIZE_BYTES = 1024L * 1024L * 1024L

    private val multiFileExtensions = setOf("cue", "gdi", "m3u")

    /** Emulators that resolve sibling tracks via real filesystem paths. */
    private val needsRealPathPackages = setOf(
        "com.github.stenzek.duckstation"
    )

    fun launch(context: Context, spec: LaunchSpec): LaunchResult {
        try {
            if (spec.killBeforeLaunch) {
                killBackgroundProcesses(context, spec.packageName)
            }

            val intent = Intent()
            if (spec.activityName != null) {
                intent.component = ComponentName(spec.packageName, spec.activityName)
            } else {
                intent.setPackage(spec.packageName)
            }
            intent.action = spec.action ?: Intent.ACTION_MAIN

            if (spec.category != null) {
                intent.addCategory(spec.category)
            } else if (spec.action == null || spec.action == Intent.ACTION_MAIN) {
                intent.addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolvedData = spec.data?.let { resolveMarkedValue(context, it) }
            val resolvedExtras = spec.extras.map { extra ->
                val resolved = resolveMarkedValue(context, extra.value)
                if (resolved == extra.value) extra else extra.copy(value = resolved)
            }

            var uriData: Uri? = resolvedData?.let {
                if (!it.contains("://") && it.startsWith("/")) Uri.parse("file://$it")
                else Uri.parse(it)
            }

            val needsRealPathForMultiFile = spec.packageName in needsRealPathPackages
            var masterRealPath: String? = null
            var isMultiFile = false

            if (uriData?.scheme == "content") {
                masterRealPath = resolveSafUriToPath(uriData)
                val masterFileName = getFileNameFromUri(context, uriData) ?: ""
                val masterExt = masterFileName.substringAfterLast('.', "").lowercase()
                isMultiFile = masterExt in multiFileExtensions

                if (masterRealPath != null && !isMultiFile && !spec.keepSafUri) {
                    // Single-file ROMs: rewrap through our FileProvider so emulators
                    // save states in their private dir instead of next to the ROM.
                    resolveToFileProviderUri(context, uriData)?.let { uriData = it }
                } else if (masterRealPath != null && isMultiFile && needsRealPathForMultiFile) {
                    uriData = Uri.parse("file://$masterRealPath")
                }
            }

            when {
                uriData != null && spec.mimeType != null ->
                    intent.setDataAndType(uriData, spec.mimeType)
                uriData != null -> intent.data = uriData
                spec.mimeType != null -> intent.type = spec.mimeType
            }

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            spec.activityFlags.forEach { flag ->
                flagStringToIntent(flag)?.let { intent.addFlags(it) }
            }

            for (extra in resolvedExtras) {
                val value = extra.value

                // RetroArch: expand bare core names to the variant's cores dir
                if (spec.packageName.startsWith("com.retroarch") &&
                    extra.key == "LIBRETRO" && !value.startsWith("/")
                ) {
                    val libretroDir = getDefaultLibretroDirectory(context, spec.packageName)
                    val base = value
                        .removeSuffix("_libretro_android.so")
                        .removeSuffix("_libretro.so")
                    intent.putExtra(extra.key, "$libretroDir${base}_libretro_android.so")
                    continue
                }

                val finalValue = if (value.startsWith("content://") && needsRealPathForMultiFile) {
                    resolveMultiFileExtraToFileUri(context, value)
                } else value

                when (extra.type) {
                    "string" -> intent.putExtra(extra.key, finalValue)
                    "bool", "boolean" -> intent.putExtra(extra.key, finalValue.toBoolean())
                    "int" -> intent.putExtra(extra.key, finalValue.toIntOrNull() ?: 0)
                    "long" -> intent.putExtra(extra.key, finalValue.toLongOrNull() ?: 0L)
                    "float" -> intent.putExtra(extra.key, finalValue.toFloatOrNull() ?: 0f)
                    "uri" -> intent.putExtra(extra.key, Uri.parse(finalValue))
                    "string_array" -> intent.putExtra(
                        extra.key,
                        finalValue.split(",").map { it.trim() }.toTypedArray()
                    )
                }
            }

            // RetroArch: CONFIGFILE default per variant package
            if (spec.packageName.startsWith("com.retroarch") && !intent.hasExtra("CONFIGFILE")) {
                intent.putExtra(
                    "CONFIGFILE",
                    "/storage/emulated/0/Android/data/${spec.packageName}/files/retroarch.cfg"
                )
            }

            // Grant SAF permissions for any remaining content:// URI
            val primaryContentUri: Uri? = when {
                uriData?.scheme == "content" -> uriData
                else -> resolvedExtras.firstOrNull { it.value.startsWith("content://") }
                    ?.let { Uri.parse(it.value) }
            }
            if (primaryContentUri != null) {
                grantPrimary(context, spec.packageName, intent, primaryContentUri)
                grantParentTreePermission(context, spec.packageName, intent, primaryContentUri)
                grantSubfolderContentsIfZip(context, spec.packageName, intent, primaryContentUri)

                val masterFileName = getFileNameFromUri(context, primaryContentUri) ?: ""
                val masterExt = masterFileName.substringAfterLast('.', "").lowercase()
                if (masterExt in multiFileExtensions) {
                    if (isMultiFile && DocumentsContract.isDocumentUri(context, primaryContentUri) &&
                        !needsRealPathForMultiFile
                    ) {
                        grantSiblingTrackPermissions(context, spec.packageName, intent, primaryContentUri)
                    } else if (masterRealPath != null) {
                        grantSiblingFileProviderPermissions(context, spec.packageName, intent, masterRealPath)
                    }
                }
            }

            // Allow file:// URIs for legacy emulators
            try {
                StrictMode::class.java.getMethod("disableDeathOnFileUriExposure").invoke(null)
            } catch (_: Exception) {
            }

            // Don't pre-resolve — returns null on Android 11+ for visible-but-unqueried
            // apps. Catch ActivityNotFoundException instead.
            val options = spec.launchDisplayId?.let {
                ActivityOptions.makeBasic().setLaunchDisplayId(it).toBundle()
            }
            if (options != null) {
                context.startActivity(intent, options)
            } else {
                context.startActivity(intent)
            }
            return LaunchResult.Success
        } catch (e: ActivityNotFoundException) {
            return if (!isPackageInstalled(context, spec.packageName)) {
                LaunchResult.EmulatorNotInstalled(spec.packageName)
            } else {
                LaunchResult.ActivityNotFound(spec.packageName)
            }
        } catch (e: SecurityException) {
            return LaunchResult.PermissionDenied(e.message)
        } catch (e: Exception) {
            return LaunchResult.Failed(e.message)
        }
    }

    fun isPackageInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (_: Exception) {
        false
    }

    private fun killBackgroundProcesses(context: Context, packageName: String) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(packageName)
        } catch (_: Exception) {
        }
    }

    private fun grantPrimary(context: Context, packageName: String, intent: Intent, uri: Uri) {
        // Synchronous grant — FLAG_GRANT_READ_URI_PERMISSION alone is processed
        // asynchronously and can lose the first-launch race.
        try {
            context.grantUriPermission(
                packageName,
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        if (intent.clipData == null) {
            intent.clipData = ClipData.newRawUri("ROM", uri)
        }
    }

    private fun resolveMarkedValue(context: Context, value: String): String = when {
        value.startsWith("wajiha-realpath:") -> {
            val raw = value.removePrefix("wajiha-realpath:")
            if (raw.startsWith("content://")) {
                val uri = Uri.parse(raw)
                resolveSafUriToPath(uri) ?: run {
                    val fileName = getFileNameFromUri(context, uri) ?: "rom"
                    cacheContentUriToFile(context, uri, fileName)?.absolutePath ?: raw
                }
            } else raw
        }
        value.startsWith("wajiha-localuri:") -> {
            val raw = value.removePrefix("wajiha-localuri:")
            when {
                raw.startsWith("content://") -> raw
                raw.startsWith("file://") -> raw
                else -> "file://$raw"
            }
        }
        else -> value
    }

    private fun flagStringToIntent(flag: String): Int? = when (flag) {
        "clear-task" -> Intent.FLAG_ACTIVITY_CLEAR_TASK
        "clear-top" -> Intent.FLAG_ACTIVITY_CLEAR_TOP
        "no-animation" -> Intent.FLAG_ACTIVITY_NO_ANIMATION
        "no-history" -> Intent.FLAG_ACTIVITY_NO_HISTORY
        "single-top" -> Intent.FLAG_ACTIVITY_SINGLE_TOP
        "reorder-to-front" -> Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        "reset-task-if-needed" -> Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        "brought-to-front" -> Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
        else -> flag.removePrefix("raw:").toIntOrNull()
    }

    private fun resolveSafUriToPath(uri: Uri): String? {
        try {
            if (uri.authority != "com.android.externalstorage.documents") return null
            var docId = DocumentsContract.getDocumentId(uri)
            if (docId.contains("%3A") || docId.contains("%3a")) {
                docId = Uri.decode(docId)
            }
            val split = docId.split(":")
            if (split.size < 2) return null
            val (type, path) = split
            return if ("primary".equals(type, ignoreCase = true)) {
                Environment.getExternalStorageDirectory().toString() + "/" + path
            } else {
                "/storage/$type/$path"
            }
        } catch (_: Exception) {
            return null
        }
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) result = cursor.getString(idx)
                }
            }
        }
        return result ?: uri.lastPathSegment
    }

    private fun cacheContentUriToFile(context: Context, uri: Uri, fileName: String): File? {
        try {
            // Public dir so the emulator can read the cached copy (NAS/remote SAF
            // providers have no filesystem path).
            val publicDir = File(Environment.getExternalStorageDirectory(), "Wajiha/$ROM_IMPORT_DIR")
            val importDir = if (publicDir.mkdirs() || publicDir.exists()) publicDir
            else File(context.externalCacheDir ?: context.cacheDir, ROM_IMPORT_DIR).also { it.mkdirs() }

            cleanupOldCacheFiles(importDir)
            val destFile = File(importDir, fileName)

            var remoteSize = -1L
            context.contentResolver.query(
                uri, arrayOf(OpenableColumns.SIZE), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0) remoteSize = cursor.getLong(idx)
                }
            }

            if (destFile.exists() && destFile.length() == remoteSize && remoteSize > 0) {
                return destFile
            }
            if (remoteSize > MAX_ROM_CACHE_SIZE_BYTES) return null

            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            return destFile
        } catch (_: Exception) {
            return null
        }
    }

    private fun cleanupOldCacheFiles(dir: File) {
        try {
            val cutoff = System.currentTimeMillis() - MAX_CACHE_AGE_MS
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < cutoff) file.delete()
            }
        } catch (_: Exception) {
        }
    }

    private fun getDefaultLibretroDirectory(context: Context, retroArchPackage: String): String =
        try {
            val appInfo = context.packageManager.getApplicationInfo(retroArchPackage, 0)
            "${appInfo.dataDir}/cores/"
        } catch (_: Exception) {
            "/data/user/0/$retroArchPackage/cores/"
        }

    private fun grantParentTreePermission(
        context: Context,
        packageName: String,
        intent: Intent,
        fileUri: Uri
    ) {
        try {
            if (!DocumentsContract.isDocumentUri(context, fileUri)) return
            val treeDocId = DocumentsContract.getTreeDocumentId(fileUri)
            val treeUri = DocumentsContract.buildTreeDocumentUri(fileUri.authority, treeDocId)

            context.grantUriPermission(
                packageName,
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)

            if (intent.clipData == null) {
                intent.clipData = ClipData.newRawUri("ROM_DIR", treeUri)
            } else {
                intent.clipData?.addItem(ClipData.Item(treeUri))
            }
        } catch (_: Exception) {
        }
    }

    private fun grantSubfolderContentsIfZip(
        context: Context,
        packageName: String,
        intent: Intent,
        fileUri: Uri
    ) {
        try {
            if (!DocumentsContract.isDocumentUri(context, fileUri)) return
            val fileName = getFileNameFromUri(context, fileUri) ?: return
            if (!fileName.endsWith(".zip", ignoreCase = true)) return

            val docId = DocumentsContract.getDocumentId(fileUri)
            if (!docId.contains('/')) return
            val authority = fileUri.authority ?: return
            val folderName = fileName.substringBeforeLast('.', "")
            if (folderName.isEmpty()) return

            val parentDocId = docId.substringBeforeLast('/')
            val parentTreeUri = DocumentsContract.buildTreeDocumentUri(authority, parentDocId)
            val parentChildrenUri =
                DocumentsContract.buildChildDocumentsUriUsingTree(parentTreeUri, parentDocId)

            var subfolderDocId: String? = null
            context.contentResolver.query(
                parentChildrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val childDocId = cursor.getString(0) ?: continue
                    val childName = cursor.getString(1) ?: continue
                    val mimeType = cursor.getString(2) ?: continue
                    if (childName == folderName && mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        subfolderDocId = childDocId
                        break
                    }
                }
            }
            val folderDocId = subfolderDocId ?: return

            val subfolderTreeUri = DocumentsContract.buildTreeDocumentUri(authority, folderDocId)
            try {
                context.grantUriPermission(
                    packageName,
                    subfolderTreeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                intent.clipData?.addItem(ClipData.Item(subfolderTreeUri))
            } catch (_: Exception) {
            }

            val subfolderChildrenUri =
                DocumentsContract.buildChildDocumentsUriUsingTree(subfolderTreeUri, folderDocId)
            context.contentResolver.query(
                subfolderChildrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val childDocId = cursor.getString(0) ?: continue
                    val childUri =
                        DocumentsContract.buildDocumentUriUsingTree(subfolderTreeUri, childDocId)
                    try {
                        context.grantUriPermission(
                            packageName,
                            childUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                        intent.clipData?.addItem(ClipData.Item(childUri))
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun grantSiblingTrackPermissions(
        context: Context,
        packageName: String,
        intent: Intent,
        masterUri: Uri
    ) {
        val trackExts = setOf("bin", "iso", "img", "sub", "wav", "flac", "dat")
        try {
            val treeDocId = DocumentsContract.getTreeDocumentId(masterUri)
            val docId = DocumentsContract.getDocumentId(masterUri)
            val parentDocId = if (docId.contains('/')) docId.substringBeforeLast('/') else treeDocId
            val treeUri = DocumentsContract.buildTreeDocumentUri(masterUri.authority, treeDocId)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)

            val masterFileName = getFileNameFromUri(context, masterUri) ?: ""
            val masterBase = masterFileName.substringBeforeLast('.').lowercase()

            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val childDocId = cursor.getString(0) ?: continue
                    val childName = cursor.getString(1) ?: continue
                    val childExt = childName.substringAfterLast('.', "").lowercase()
                    val childBase = childName.substringBeforeLast('.').lowercase()

                    val sameBaseName = childBase == masterBase
                    val isTrackSibling =
                        childExt in trackExts && childName.lowercase().startsWith(masterBase)

                    if (sameBaseName || isTrackSibling) {
                        val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                        try {
                            context.grantUriPermission(
                                packageName, childUri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                            intent.clipData?.addItem(ClipData.Item(childUri))
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun resolveToFileProviderUri(context: Context, contentUri: Uri): Uri? {
        val realPath = resolveSafUriToPath(contentUri) ?: return null
        val file = File(realPath)
        if (!file.exists()) return null
        return try {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveMultiFileExtraToFileUri(context: Context, value: String): String {
        if (!value.startsWith("content://")) return value
        val uri = Uri.parse(value)
        val fileName = getFileNameFromUri(context, uri) ?: return value
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext !in multiFileExtensions) return value
        val realPath = resolveSafUriToPath(uri) ?: return value
        return "file://$realPath"
    }

    private fun grantSiblingFileProviderPermissions(
        context: Context,
        packageName: String,
        intent: Intent,
        masterRealPath: String
    ) {
        val trackExts = setOf("bin", "iso", "img", "sub", "wav", "flac", "dat", "raw", "ogg", "mp3")
        try {
            val masterFile = File(masterRealPath)
            val parentDir = masterFile.parentFile ?: return
            val masterBase = masterFile.nameWithoutExtension.lowercase()

            parentDir.listFiles()?.forEach { file ->
                if (file == masterFile) return@forEach
                val childExt = file.extension.lowercase()
                val childBase = file.nameWithoutExtension.lowercase()

                val sameBaseName = childBase == masterBase
                val isTrackSibling =
                    childExt in trackExts && file.name.lowercase().startsWith(masterBase)

                if (sameBaseName || isTrackSibling) {
                    try {
                        val siblingUri = FileProvider.getUriForFile(
                            context, "${context.packageName}.fileprovider", file
                        )
                        context.grantUriPermission(
                            packageName,
                            siblingUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                        intent.clipData?.addItem(ClipData.Item(siblingUri))
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
    }
}
