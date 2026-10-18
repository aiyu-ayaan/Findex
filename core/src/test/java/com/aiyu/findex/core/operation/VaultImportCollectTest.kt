package com.aiyu.findex.core.operation

import com.aiyu.findex.core.model.FailureReason
import com.aiyu.findex.core.model.FileOperationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.file.Files

class VaultImportCollectTest {
    private val storage = Files.createTempDirectory("storage").toFile().apply { deleteOnExit() }
    private val vault = storage.resolve(".FindexVault").apply { mkdirs(); resolve("vault.key").writeText("k"); resolve("x.fdxv").writeText("b") }

    @Test fun rootContainingVaultIsRejected() {
        val e = assertThrows(FileOperationException::class.java) { VaultOperations.collect(listOf(storage.path), vault) }
        assertEquals(FailureReason.INTO_ITSELF, e.reason)
    }

    @Test fun vaultFilesNeverCollected() {
        val photos = storage.resolve("Photos").apply { mkdirs(); resolve("a.jpg").writeText("a") }
        val lookalike = storage.resolve(".FindexVaultOld").apply { mkdirs(); resolve("b.txt").writeText("b") }
        val (roots, files) = VaultOperations.collect(listOf(photos.path, lookalike.path, vault.resolve("x.fdxv").path), vault)
        assertEquals(listOf(photos, lookalike), roots)
        assertEquals(setOf("a.jpg", "b.txt"), files.map { it.name }.toSet())
    }
}
