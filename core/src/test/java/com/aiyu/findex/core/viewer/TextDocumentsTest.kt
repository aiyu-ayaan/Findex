package com.aiyu.findex.core.viewer

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TextDocumentsTest {
    private val docs = TextDocuments()
    private fun temp(bytes: ByteArray) = File.createTempFile("fdx", ".txt").apply { writeBytes(bytes); deleteOnExit() }

    @Test fun roundTripKeepsBomAndCrlf() = runBlocking {
        val f = temp(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "a\r\nb".toByteArray())
        val d = docs.load(f.path)
        assertEquals("a\nb", d.text)
        assertTrue(d.bom)
        assertEquals(SaveResult.Saved, docs.save(f.path, "x\ny", d))
        assertEquals("x\r\ny", f.readBytes().drop(3).toByteArray().decodeToString())
    }

    @Test fun latin1FallbackAndBinaryReject() = runBlocking {
        assertEquals(Charsets.ISO_8859_1, docs.load(temp(byteArrayOf(0x63, 0xE9.toByte())).path).charset)
        try { docs.load(temp(byteArrayOf(1, 0, 2)).path); error("expected") } catch (_: NotTextException) {}
    }

    @Test fun conflictWhenModified() = runBlocking {
        val f = temp("a".toByteArray())
        val d = docs.load(f.path)
        f.setLastModified(d.lastModified + 5000)
        assertEquals(SaveResult.Conflict, docs.save(f.path, "b", d))
    }
}
