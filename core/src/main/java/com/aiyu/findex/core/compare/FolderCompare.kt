package com.aiyu.findex.core.compare

import com.aiyu.findex.core.model.StorageItem
import kotlin.math.abs

/** How an item relates to the item of the same name in the other folder. */
enum class CompareStatus { ONLY_HERE, NEWER, OLDER, DIFFERENT, SAME }

/** One level of two folders matched by name; a file and a folder sharing a name don't match. */
object FolderCompare {
    /** FAT and most SMB servers store modification times in 2 s steps. */
    const val TIME_TOLERANCE_MS = 2_000L

    /**
     * @param folderSignature content summary of a folder (e.g. file count + bytes); folders whose signatures
     *   differ are [CompareStatus.DIFFERENT]. Null means unknown and same-name folders count as [CompareStatus.SAME].
     * @return statuses by path for [left] and for [right].
     */
    fun compare(
        left: List<StorageItem>,
        right: List<StorageItem>,
        folderSignature: (StorageItem) -> Any? = { null }
    ): Pair<Map<String, CompareStatus>, Map<String, CompareStatus>> {
        val rightByKey = right.associateBy { it.isDirectory to it.name }
        val leftStatus = HashMap<String, CompareStatus>()
        val rightStatus = HashMap<String, CompareStatus>()
        for (a in left) {
            val b = rightByKey[a.isDirectory to a.name]
            if (b == null) {
                leftStatus[a.path] = CompareStatus.ONLY_HERE
                continue
            }
            val (sa, sb) = if (a.isDirectory) folderPair(folderSignature(a), folderSignature(b)) else filePair(a, b)
            leftStatus[a.path] = sa
            rightStatus[b.path] = sb
        }
        for (b in right) rightStatus.putIfAbsent(b.path, CompareStatus.ONLY_HERE)
        return leftStatus to rightStatus
    }

    private fun filePair(a: StorageItem, b: StorageItem): Pair<CompareStatus, CompareStatus> {
        val dt = a.lastModified - b.lastModified
        return when {
            abs(dt) <= TIME_TOLERANCE_MS -> if (a.size == b.size) CompareStatus.SAME to CompareStatus.SAME else CompareStatus.DIFFERENT to CompareStatus.DIFFERENT
            dt > 0 -> CompareStatus.NEWER to CompareStatus.OLDER
            else -> CompareStatus.OLDER to CompareStatus.NEWER
        }
    }

    private fun folderPair(a: Any?, b: Any?): Pair<CompareStatus, CompareStatus> =
        if (a != null && b != null && a != b) CompareStatus.DIFFERENT to CompareStatus.DIFFERENT else CompareStatus.SAME to CompareStatus.SAME
}
