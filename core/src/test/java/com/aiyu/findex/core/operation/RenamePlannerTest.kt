package com.aiyu.findex.core.operation

import com.aiyu.findex.core.model.RenameEntry
import com.aiyu.findex.core.model.StorageItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenamePlannerTest {
    private fun item(name: String, dir: String = "/d", isDir: Boolean = false) =
        StorageItem(path = "$dir/$name", name = name, isDirectory = isDir)

    private fun plan(items: List<StorageItem>, rule: RenameRule, extra: Set<String> = emptySet()) =
        RenamePlanner.plan(items, rule, items.groupBy { RenamePlanner.parentOf(it.path) }.mapValues { (_, v) -> v.map { it.name }.toSet() + extra })

    @Test fun counterKeepsExtension() {
        val r = plan(listOf(item("a.jpg"), item("b.png")), RenameRule(pattern = "img_{n:3}", counterStart = 5, counterStep = 2))
        assertEquals(listOf("img_005.jpg", "img_007.png"), r.map { it.newName })
    }

    @Test fun touchExtensionCasesWholeName() {
        val r = plan(listOf(item("a.JPG")), RenameRule(case = CaseMode.LOWER, touchExtension = true))
        assertEquals("a.jpg", r[0].newName)
        assertEquals("A.jpg", plan(listOf(item("a.jpg")), RenameRule(case = CaseMode.UPPER))[0].newName)
    }

    @Test fun findReplaceThenPattern() {
        val r = plan(listOf(item("holiday photo.jpg")), RenameRule(pattern = "{parent}-{name}", find = " ", replace = "_"))
        assertEquals("d-holiday_photo.jpg", r[0].newName)
    }

    @Test fun badRegex() {
        assertEquals(RenameStatus.BAD_REGEX, plan(listOf(item("a")), RenameRule(find = "(", regex = true))[0].status)
    }

    @Test fun duplicatesCaseInsensitive() {
        val r = plan(listOf(item("a.txt"), item("b.txt")), RenameRule(pattern = "X"))
        assertEquals(listOf(RenameStatus.DUPLICATE, RenameStatus.DUPLICATE), r.map { it.status })
    }

    @Test fun existingOutsideBatchConflicts() {
        val r = plan(listOf(item("a.txt")), RenameRule(pattern = "keep"), extra = setOf("KEEP.txt"))
        assertEquals(RenameStatus.EXISTS, r[0].status)
    }

    @Test fun collapsingToOneNameAndCaseOnly() {
        val r = plan(listOf(item("a"), item("b")), RenameRule(find = "a|b", regex = true, replace = "z"))
        assertEquals(RenameStatus.DUPLICATE, r[0].status)
        val c = plan(listOf(item("a.txt")), RenameRule(case = CaseMode.UPPER, touchExtension = false))
        assertEquals("A.txt", c[0].newName)
        assertEquals(RenameStatus.OK, c[0].status)
    }

    @Test fun invalidNames() {
        assertEquals(RenameStatus.INVALID, plan(listOf(item("a")), RenameRule(pattern = ""))[0].status)
        assertEquals(RenameStatus.INVALID, plan(listOf(item("a")), RenameRule(pattern = "x/y"))[0].status)
        assertEquals(RenameStatus.INVALID, plan(listOf(item("a")), RenameRule(pattern = "x".repeat(256)))[0].status)
    }

    @Test fun cycleUsesTempNames() {
        val s = RenameSchedule.of(listOf(RenameEntry("/d/a", "b"), RenameEntry("/d/b", "a")), "t")
        assertEquals(listOf(RenameMove("/d/a", "/d/.fdxrn-t-0"), RenameMove("/d/b", "/d/.fdxrn-t-1")), s.phase1)
        assertEquals(listOf(RenameMove("/d/.fdxrn-t-0", "/d/b"), RenameMove("/d/.fdxrn-t-1", "/d/a")), s.phase2)
    }

    @Test fun chainAndPlainAndCaseOnly() {
        val s = RenameSchedule.of(listOf(RenameEntry("/d/a", "b"), RenameEntry("/d/b", "c"), RenameEntry("/d/x", "y"), RenameEntry("/d/f", "F")), "t")
        assertEquals(setOf("/d/a", "/d/b", "/d/f"), s.phase1.map { it.from }.toSet())
        assertEquals(RenameMove("/d/x", "/d/y"), s.phase2.single { it.from == "/d/x" })
    }

    @Test fun caseOnlyRenameOntoDistinctFileConflicts() {
        // Case-sensitive volume: a.txt and A.txt are two files; a.txt -> A.txt must not clobber A.txt.
        val r = plan(listOf(item("a.txt")), RenameRule(case = CaseMode.UPPER), extra = setOf("A.txt"))
        assertEquals(RenameStatus.EXISTS, r[0].status)
        assertTrue(RenamePlanner.clashes("A.txt", listOf("a.txt", "A.txt"), setOf("a.txt")))
        assertFalse(RenamePlanner.clashes("A.txt", listOf("a.txt"), setOf("a.txt")))
        assertFalse(RenamePlanner.clashes("b", listOf("a", "b"), setOf("a", "b")))
    }

    @Test fun nestedSelectionDetected() {
        assertTrue(RenamePlanner.hasNested(listOf("/d/folder", "/d/folder/child.txt")))
        assertFalse(RenamePlanner.hasNested(listOf("/d/folder", "/d/folder2/child.txt", "/d/a")))
    }

    /** Mirrors PathIndexDao.onMoved: UPDATE OR REPLACE re-keys a path, dropping any row already there. */
    private fun replay(data: Map<String, String>, moves: List<RenameMove>): Map<String, String> {
        val m = data.toMutableMap()
        for (mv in moves) m.remove(mv.from)?.let { m[mv.to] = it }
        return m
    }

    @Test fun replayingScheduleKeepsDataOnSwapAndChain() {
        val swap = RenameSchedule.of(listOf(RenameEntry("/d/a", "b"), RenameEntry("/d/b", "a")), "t")
        assertEquals(mapOf("/d/b" to "tagA", "/d/a" to "tagB"), replay(mapOf("/d/a" to "tagA", "/d/b" to "tagB"), swap.moves))
        val chain = RenameSchedule.of(listOf(RenameEntry("/d/a", "b"), RenameEntry("/d/b", "c")), "t")
        assertEquals(mapOf("/d/b" to "tagA", "/d/c" to "tagB"), replay(mapOf("/d/a" to "tagA", "/d/b" to "tagB"), chain.moves))
    }
}
