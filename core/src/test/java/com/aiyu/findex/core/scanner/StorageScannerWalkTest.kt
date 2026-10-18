package com.aiyu.findex.core.scanner

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class StorageScannerWalkTest {
    @Test fun walkSkipsVaultTrashAndPrivateAppDirs() {
        val root = Files.createTempDirectory("scanner-root").toFile().apply { deleteOnExit() }
        root.resolve(".FindexVault").apply { mkdirs(); resolve("vault.key").writeText("k"); resolve("a.fdxv").writeText("b") }
        root.resolve(".FindexTrash").apply { mkdirs(); resolve("old.txt").writeText("x") }
        root.resolve("Android/data/com.example").apply { mkdirs(); resolve("cache.tmp").writeText("c") }
        root.resolve("Android/obb/com.example").apply { mkdirs(); resolve("main.obb").writeText("o") }
        root.resolve("DCIM").apply { mkdirs(); resolve("photo.jpg").writeText("p") }

        val names = StorageScanner.walk(root).filter { it != root }.map { it.name }.toSet()
        assertEquals(setOf("Android", "DCIM", "photo.jpg"), names)
    }
}
