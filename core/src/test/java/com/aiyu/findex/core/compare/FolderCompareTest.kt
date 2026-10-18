package com.aiyu.findex.core.compare

import com.aiyu.findex.core.model.StorageItem
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderCompareTest {
    private fun file(dir: String, name: String, size: Long, time: Long) = StorageItem("$dir/$name", name, size, time)
    private fun folder(dir: String, name: String) = StorageItem("$dir/$name", name, isDirectory = true)

    @Test fun matchesByNameAndKind() {
        val left = listOf(
            file("/a", "same.txt", 10, 100_000),
            file("/a", "jitter.txt", 10, 101_500), // within 2 s
            file("/a", "new.txt", 10, 200_000),
            file("/a", "size.txt", 10, 100_000),
            file("/a", "only.txt", 1, 0),
            folder("/a", "docs"),
            folder("/a", "pics"),
            file("/a", "kind", 1, 0),
        )
        val right = listOf(
            file("/b", "same.txt", 10, 100_000),
            file("/b", "jitter.txt", 10, 100_000),
            file("/b", "new.txt", 99, 100_000),
            file("/b", "size.txt", 11, 100_000),
            folder("/b", "docs"),
            folder("/b", "pics"),
            folder("/b", "kind"),
        )
        val signatures = mapOf("/a/docs" to 1, "/b/docs" to 2, "/a/pics" to 5, "/b/pics" to 5)

        val (l, r) = FolderCompare.compare(left, right) { signatures[it.path] }

        assertEquals(CompareStatus.SAME, l["/a/same.txt"])
        assertEquals(CompareStatus.SAME, l["/a/jitter.txt"])
        assertEquals(CompareStatus.NEWER, l["/a/new.txt"])
        assertEquals(CompareStatus.OLDER, r["/b/new.txt"])
        assertEquals(CompareStatus.DIFFERENT, r["/b/size.txt"])
        assertEquals(CompareStatus.ONLY_HERE, l["/a/only.txt"])
        assertEquals(CompareStatus.DIFFERENT, l["/a/docs"])
        assertEquals(CompareStatus.SAME, r["/b/pics"])
        assertEquals(CompareStatus.ONLY_HERE, l["/a/kind"])
        assertEquals(CompareStatus.ONLY_HERE, r["/b/kind"])
        assertEquals(left.size, l.size)
        assertEquals(right.size, r.size)
    }
}
