package com.aiyu.findex.core.scanner

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import com.aiyu.findex.core.repository.toStorageItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class AppJunkType { LEFTOVERS, APP_CACHE }

/** Folders under Android/media, data and obb named after apps that are no longer installed, plus reachable external caches. */
@Singleton
class AppJunkScanner @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun scan(): List<ScanGroup<AppJunkType>> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        // Apps uninstalled with "keep data" still count as installed, so their folders are never offered.
        val installed = pm.getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES).mapTo(HashSet()) { it.packageName }
        val android = File(Environment.getExternalStorageDirectory(), "Android")
        val junk = appJunk(android, installed, context.packageName)
        listOf(
            ScanGroup(AppJunkType.LEFTOVERS, junk[AppJunkType.LEFTOVERS].orEmpty().map { it.withTreeSize() }.sortedByDescending { it.size }),
            ScanGroup(AppJunkType.APP_CACHE, junk[AppJunkType.APP_CACHE].orEmpty().map { it.withTreeSize() }.sortedByDescending { it.size }),
        ).filter { it.files.isNotEmpty() }
    }

    internal companion object {
        private val SCOPED_DIRS = listOf("media", "data", "obb")

        /**
         * [installed] must hold every package on the device (QUERY_ALL_PACKAGES); a filtered list would make
         * installed apps look removed. Android 11+ hides Android/data and usually Android/obb, so those are simply skipped.
         */
        internal fun appJunk(android: File, installed: Set<String>, self: String): Map<AppJunkType, List<File>> {
            val leftovers = mutableListOf<File>()
            val caches = mutableListOf<File>()
            for (scoped in SCOPED_DIRS) {
                val dirs = File(android, scoped).listFiles()?.filter { it.isDirectory } ?: continue
                for (dir in dirs) {
                    val pkg = dir.name
                    if (pkg == self || pkg.startsWith(".") || '.' !in pkg) continue
                    if (pkg !in installed) leftovers += dir
                    else if (scoped == "data") File(dir, "cache").takeIf { it.isDirectory && !it.list().isNullOrEmpty() }?.let { caches += it }
                }
            }
            return mapOf(AppJunkType.LEFTOVERS to leftovers, AppJunkType.APP_CACHE to caches)
        }
    }

    private fun File.withTreeSize() = toStorageItem().copy(size = walkTopDown().filter { it.isFile }.sumOf { it.length() })
}
