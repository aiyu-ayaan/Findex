package com.aiyu.findex.core.scanner

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CleanerScansTest {
    private fun tempRoot(): File = Files.createTempDirectory("cleaner-root").toFile().apply { deleteOnExit() }

    @Test fun junkFindsTopmostEmptyTreesAndZeroByteFiles() = runBlocking {
        val root = tempRoot()
        root.resolve("Movies").mkdirs() // top-level standard folder: never offered
        root.resolve("Docs/a/b/c").mkdirs() // nested empty tree: only "a" is offered
        root.resolve("Docs/keep").apply { mkdirs(); resolve("note.txt").writeText("x") }
        root.resolve("Docs/zero.txt").createNewFile()
        root.resolve("Docs/keep/.nomedia").createNewFile()
        root.resolve("Docs/hidden/.FindexVault").apply { mkdirs(); resolve("blob").writeText("v") } // skipped by walk, still not empty
        root.resolve("Android/media/com.gone").mkdirs()

        val junk = StorageScanner.junkFiles(root)

        assertEquals(listOf(root.resolve("Docs/a")), junk[JunkType.EMPTY_FOLDERS])
        assertEquals(listOf(root.resolve("Docs/zero.txt")), junk[JunkType.ZERO_BYTE_FILES])
    }

    @Test fun appMediaGroupsByKind() {
        val root = tempRoot()
        root.resolve("Android/media/org.telegram.messenger/Telegram/Telegram Images").apply { mkdirs(); resolve("a.jpg").writeText("i") }
        root.resolve("Android/media/org.telegram.messenger/Telegram/Telegram Video").apply { mkdirs(); resolve("b.mp4").writeText("v"); resolve(".nomedia").createNewFile() }
        root.resolve("Telegram/Telegram Documents").apply { mkdirs(); resolve("c.pdf").writeText("d") }

        val groups = StorageScanner.appMediaFiles(root, MediaApp.TELEGRAM).associate { (type, files) -> type to files.map { it.name } }

        assertEquals(mapOf(AppMediaType.IMAGES to listOf("a.jpg"), AppMediaType.VIDEOS to listOf("b.mp4"), AppMediaType.DOCUMENTS to listOf("c.pdf")), groups)
    }

    @Test fun leftoversOnlyForUninstalledPackages() {
        val android = tempRoot()
        android.resolve("media/com.gone.app").mkdirs()
        android.resolve("media/com.still.here").mkdirs()
        android.resolve("media/.hidden.pkg").mkdirs()
        android.resolve("obb/com.gone.app").mkdirs()
        android.resolve("data/com.still.here/cache").apply { mkdirs(); resolve("c.bin").writeText("c") }
        android.resolve("data/com.aiyu.findex").mkdirs()

        val junk = AppJunkScanner.appJunk(android, installed = setOf("com.still.here"), self = "com.aiyu.findex")

        assertEquals(setOf(android.resolve("media/com.gone.app"), android.resolve("obb/com.gone.app")), junk[AppJunkType.LEFTOVERS]!!.toSet())
        assertEquals(listOf(android.resolve("data/com.still.here/cache")), junk[AppJunkType.APP_CACHE])
    }
}
