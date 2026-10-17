package com.aiyu.findex.core.operation

import com.aiyu.findex.core.model.ArchiveFormat
import com.aiyu.findex.core.model.FileOperationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ArchiveCodecsTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun sampleTree(): File = tmp.newFolder("photos").apply {
        File(this, "a.txt").writeText("alpha")
        File(this, "nested").mkdirs()
        File(this, "nested/b.bin").writeBytes(ByteArray(300_000) { it.toByte() })
    }

    private fun assertRoundTrip(format: ArchiveFormat, write: suspend (List<File>, File) -> Unit) = runBlocking {
        val src = sampleTree()
        val archive = File(tmp.root, "out.${format.extension}")
        write(listOf(src), archive)
        val dest = tmp.newFolder("dest-${format.name}")
        var total = -1L
        ArchiveCodecs.read(format, archive, dest, null, onTotal = { total = it }, onBytes = { _, _ -> })
        assertEquals("alpha", File(dest, "photos/a.txt").readText())
        assertEquals(300_000L, File(dest, "photos/nested/b.bin").length())
        assert(total > 0)
    }

    @Test fun sevenZipRoundTrip() = assertRoundTrip(ArchiveFormat.SEVEN_Z) { s, a -> ArchiveCodecs.write7z(s, a) { _, _ -> } }

    @Test fun tarRoundTrip() = assertRoundTrip(ArchiveFormat.TAR) { s, a -> ArchiveCodecs.writeTar(s, a, gzip = false) { _, _ -> } }

    @Test fun tarGzRoundTrip() = assertRoundTrip(ArchiveFormat.TAR_GZ) { s, a -> ArchiveCodecs.writeTar(s, a, gzip = true) { _, _ -> } }

    @Test fun rejectsZipSlip() {
        assertThrows(FileOperationException::class.java) { ArchiveCodecs.safeTarget(tmp.root, "../evil.txt") }
    }

    @Test fun detectsFormatsAndKeepsSuffixWhole() {
        assertEquals(ArchiveFormat.TAR_GZ, ArchiveFormat.of("Backup.TGZ"))
        assertEquals(ArchiveFormat.TAR_XZ, ArchiveFormat.of("x.tar.xz"))
        assertEquals(null, ArchiveFormat.of("notes.gz"))
        assertEquals("photos", ArchiveFormat.baseName("photos.tar.gz"))
        File(tmp.root, "a.tar.gz").createNewFile()
        assertEquals("a (1).tar.gz", uniqueTarget(tmp.root, "a.tar.gz").name)
    }
}
