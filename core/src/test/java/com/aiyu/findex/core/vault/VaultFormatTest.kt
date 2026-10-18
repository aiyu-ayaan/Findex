package com.aiyu.findex.core.vault

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import kotlin.random.Random

class VaultFormatTest {
    private val dek = VaultFormat.randomBytes(32)
    private val meta = VaultMeta("photo.jpg", "image/jpeg", 0, 1234L, "/sdcard/DCIM/photo.jpg")

    private fun seal(plain: ByteArray, key: ByteArray = dek): ByteArray =
        ByteArrayOutputStream().also { VaultFormat.encrypt(plain.inputStream(), it, key, meta.copy(size = plain.size.toLong())) {} }.toByteArray()

    private fun unseal(blob: ByteArray, key: ByteArray = dek): ByteArray =
        ByteArrayOutputStream().also { VaultFormat.decrypt(blob.inputStream(), it, key) {} }.toByteArray()

    @Test
    fun roundTripAcrossChunkBoundaries() {
        for (size in listOf(0, 1, VaultFormat.CHUNK - 1, VaultFormat.CHUNK, VaultFormat.CHUNK + 1, 3 * VaultFormat.CHUNK + 77)) {
            val plain = Random(size).nextBytes(size)
            val blob = seal(plain)
            assertArrayEquals("size $size", plain, unseal(blob))
            assertEquals(meta.copy(size = size.toLong()), VaultFormat.readMeta(blob.inputStream(), dek))
        }
    }

    @Test
    fun randomAccessReadsAnyRange() {
        val plain = Random(7).nextBytes(2 * VaultFormat.CHUNK + 500)
        val file = File.createTempFile("vault", ".fdxv").apply { deleteOnExit(); writeBytes(seal(plain)) }
        VaultFormat.openRandomAccess(RandomAccessFile(file, "r"), dek).use { ra ->
            assertEquals(plain.size.toLong(), ra.size)
            val buf = ByteArray(1000)
            val pos = VaultFormat.CHUNK - 300L
            assertEquals(1000, ra.readAt(pos, buf, 0, 1000))
            assertArrayEquals(plain.copyOfRange(pos.toInt(), pos.toInt() + 1000), buf)
            assertEquals(-1, ra.readAt(plain.size.toLong(), buf, 0, 10))
        }
    }

    @Test
    fun wrongKeyFails() {
        val blob = seal(ByteArray(100) { 1 })
        assertThrows(VaultCorruptException::class.java) { unseal(blob, VaultFormat.randomBytes(32)) }
    }

    @Test
    fun tamperingAndTruncationFail() {
        val plain = Random(1).nextBytes(2 * VaultFormat.CHUNK + 10)
        val blob = seal(plain)
        // Flip a byte in the header (AAD), the meta, and the last chunk.
        for (i in listOf(10, VaultFormat.HEADER + 3, blob.size - 1)) {
            val bad = blob.copyOf().also { it[i] = (it[i].toInt() xor 1).toByte() }
            assertThrows("byte $i", VaultCorruptException::class.java) { unseal(bad) }
        }
        // Drop the last chunk exactly at a chunk boundary: the "last" flag no longer matches.
        val boundary = blob.size - (10 + 16)
        assertThrows(VaultCorruptException::class.java) { unseal(blob.copyOf(boundary)) }
    }

    @Test
    fun passwordWrapRejectsWrongPassword() {
        val salt = VaultFormat.randomBytes(16)
        val kek = VaultFormat.pbkdf2("correct horse".toCharArray(), salt, 1000)
        val wrapped = VaultFormat.seal(kek, dek)
        assertArrayEquals(dek, VaultFormat.open(VaultFormat.pbkdf2("correct horse".toCharArray(), salt, 1000), wrapped))
        assertThrows(VaultCorruptException::class.java) {
            VaultFormat.open(VaultFormat.pbkdf2("wrong".toCharArray(), salt, 1000), wrapped)
        }
    }

    @Test
    fun keyFileRoundTrip() {
        val key = VaultFormat.KeyFile(320_000, VaultFormat.randomBytes(16), VaultFormat.randomBytes(60))
        val bytes = ByteArrayOutputStream().also { VaultFormat.writeKeyFile(it, key) }.toByteArray()
        val back = VaultFormat.readKeyFile(bytes.inputStream())
        assertEquals(key.iterations, back.iterations)
        assertArrayEquals(key.salt, back.salt)
        assertArrayEquals(key.wrapped, back.wrapped)
    }

    @Test
    fun lockoutDoublesAfterFiveFailures() {
        assertEquals(0, VaultKeyManager.lockoutMs(4))
        assertEquals(30_000, VaultKeyManager.lockoutMs(5))
        assertEquals(60_000, VaultKeyManager.lockoutMs(6))
    }
}
