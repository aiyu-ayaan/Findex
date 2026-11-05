package com.aiyu.findex.ui.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File

/**
 * Files other apps hand to Findex through "Open with" (ACTION_VIEW). The built-in viewers and the archive
 * browser work on paths, so a URI becomes a real path when it points into shared storage, or a cache copy.
 */
object IncomingFiles {
    private const val DIR = "incoming"

    /** A readable local path for [uri], or null when it can't be read. Blocking: call off the main thread. */
    fun resolve(context: Context, uri: Uri): String? {
        when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> return uri.path?.takeIf { File(it).canRead() }
            ContentResolver.SCHEME_CONTENT -> Unit
            else -> return null
        }
        realPath(uri)?.let { return it }
        val name = displayName(context, uri)?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "file"
        val dir = File(context.cacheDir, DIR).apply { deleteRecursively(); mkdirs() } // keep only the latest copy
        val target = File(dir, name)
        return runCatching {
            context.contentResolver.openInputStream(uri)!!.use { input -> target.outputStream().use { input.copyTo(it) } }
            target.path
        }.getOrNull()
    }

    /** True for a cache copy made by [resolve]; its folder is private and wiped on the next open. */
    fun isCopy(file: File): Boolean = file.parentFile?.let { it.name == DIR && it.parentFile?.name == "cache" } == true

    /** Where "Extract all" puts a cache copy's contents: Downloads, since the cache folder is invisible to the user. */
    fun extractParent(archive: File): File? =
        if (isCopy(archive)) Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) else archive.parentFile

    // Files app / DocumentsUI URIs name the file directly ("primary:Download/a.zip"); with all-files access we can read it in place.
    private fun realPath(uri: Uri): String? {
        if (uri.authority != "com.android.externalstorage.documents") return null
        val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        val volume = id.substringBefore(':')
        val inner = id.substringAfter(':', "")
        val base = if (volume.equals("primary", ignoreCase = true)) Environment.getExternalStorageDirectory().path else "/storage/$volume"
        return "$base/$inner".takeIf { File(it).canRead() }
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment
}
