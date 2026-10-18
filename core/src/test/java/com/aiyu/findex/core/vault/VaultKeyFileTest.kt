package com.aiyu.findex.core.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.file.Files

class VaultKeyFileTest {
    private val dir = Files.createTempDirectory("vault").toFile().apply { deleteOnExit() }

    @Test fun emptyFolderMeansNoVault() = assertNull(VaultKeyManager.readKeyFileIn(dir))

    @Test fun blobsWithoutKeyAreUnreadableNotNoVault() {
        File(dir, "a" + VaultKeyManager.BLOB_EXT).writeText("blob")
        assertThrows(VaultUnreadableException::class.java) { VaultKeyManager.readKeyFileIn(dir) }
    }

    @Test fun corruptKeyIsUnreadable() {
        File(dir, VaultKeyManager.KEY_FILE).writeText("garbage")
        assertThrows(VaultUnreadableException::class.java) { VaultKeyManager.readKeyFileIn(dir) }
    }

    @Test fun validKeyIsRead() {
        val key = VaultFormat.KeyFile(1000, ByteArray(16) { 1 }, ByteArray(48) { 2 })
        File(dir, VaultKeyManager.KEY_FILE).outputStream().use { VaultFormat.writeKeyFile(it, key) }
        assertEquals(1000, VaultKeyManager.readKeyFileIn(dir)!!.iterations)
    }
}
