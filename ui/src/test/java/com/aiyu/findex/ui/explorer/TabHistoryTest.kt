package com.aiyu.findex.ui.explorer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TabHistoryTest {
    private val root = "/storage/emulated/0"
    private val tab = ExplorerTab(id = 1, root = root, rootName = "Internal", path = root)

    private fun ExplorerTab.go(path: String) = visit(root, rootName, path)

    @Test
    fun backReturnsToPreviousFoldersInOrder() {
        val t = tab.go("$root/Download").go(root)
        val back1 = t.stepBack()!!
        assertEquals("$root/Download", back1.path)
        assertEquals(root, back1.stepBack()!!.path)
        assertNull(back1.stepBack()!!.stepBack())
    }

    @Test
    fun visitingTheSameFolderAddsNothing() {
        assertEquals(tab, tab.go(root))
    }

    @Test
    fun backSkipsEntriesEqualToTheCurrentFolder() {
        // A → A/B recorded, then "up" back to A without recording: history ends with A itself.
        val t = tab.go("$root/A").copy(path = root)
        assertNull(t.stepBack())
    }

    @Test
    fun backRestoresTheVolumeOfTheEntry() {
        val sd = tab.visit("/storage/1234-5678", "SD card", "/storage/1234-5678/Music")
        val back = sd.stepBack()!!
        assertEquals(root, back.root)
        assertEquals("Internal", back.rootName)
    }

    @Test
    fun historyIsCapped() {
        var t = tab
        repeat(80) { t = t.go("$root/f$it") }
        assertEquals(50, t.history.size)
    }
}
