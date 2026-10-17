package com.aiyu.findex.core

import com.aiyu.findex.core.model.SortBy
import com.aiyu.findex.core.model.SortSpec
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.core.model.sortedBy
import com.aiyu.findex.core.operation.uniqueTarget
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileLogicTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test fun uniqueTarget_returnsSameName_whenFree() {
        assertEquals("a.txt", uniqueTarget(tmp.root, "a.txt").name)
    }

    @Test fun uniqueTarget_appendsCounter_beforeExtension() {
        tmp.newFile("a.txt")
        tmp.newFile("a (1).txt")
        assertEquals("a (2).txt", uniqueTarget(tmp.root, "a.txt").name)
    }

    @Test fun uniqueTarget_handlesNamesWithoutExtension() {
        tmp.newFolder("Photos")
        assertEquals("Photos (1)", uniqueTarget(tmp.root, "Photos").name)
    }

    @Test fun sortedBy_keepsFoldersFirst_andRespectsDirection() {
        val items = listOf(
            StorageItem("/b", "b.txt", size = 10),
            StorageItem("/z", "z", isDirectory = true),
            StorageItem("/a", "a.txt", size = 99),
            StorageItem("/c", "c", isDirectory = true),
        )
        assertEquals(listOf("c", "z", "a.txt", "b.txt"), items.sortedBy(SortSpec(SortBy.NAME, true)).map { it.name })
        val bySizeDesc = items.sortedBy(SortSpec(SortBy.SIZE, false)).map { it.name }
        assertEquals(setOf("c", "z"), bySizeDesc.take(2).toSet())
        assertEquals(listOf("a.txt", "b.txt"), bySizeDesc.drop(2))
    }
}
