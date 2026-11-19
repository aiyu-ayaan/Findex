package com.aiyu.findex.core.link

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same vector as `sas::tests` in the desktop app (`findex-link`); both must agree or pairing codes differ. */
class LinkCryptoTest {
    @Test
    fun codeMatchesSharedVector() {
        val code = LinkCrypto.code(ByteArray(32) { 0x11 }, ByteArray(32) { 0x22 }, ByteArray(32) { 0x33 }, ByteArray(32) { 0x44 })
        assertEquals("702489", code)
        assertEquals(
            "deb0e38ced1e41de6f92e70e80c418d2d356afaaa99e26f5939dbc7d3ef4772a",
            LinkCrypto.hex(LinkCrypto.sha256(ByteArray(32) { 0x33 }))
        )
    }

    @Test
    fun hexRoundTrips() {
        val bytes = byteArrayOf(0, 1, 0xab.toByte(), 0xff.toByte())
        assertArrayEquals(bytes, LinkCrypto.unhex(LinkCrypto.hex(bytes)))
        assertNull(LinkCrypto.unhex("abc"))
        assertNull(LinkCrypto.unhex("zz"))
        assertEquals(64, LinkCrypto.token().length)
        assertTrue(LinkCrypto.same("abc", "abc"))
        assertFalse(LinkCrypto.same("abc", "abd"))
    }

    @Test
    fun pathsRejectEscapes() {
        assertEquals(listOf("Internal storage", "DCIM", "a b.jpg"), LinkPaths.split("/Internal storage/DCIM/a b.jpg"))
        assertEquals(emptyList<String>(), LinkPaths.split("/"))
        assertNull(LinkPaths.split("/Internal storage/../x"))
        assertNull(LinkPaths.split("/Internal storage/./x"))
        assertNull(LinkPaths.split("/a\\b"))
    }

    @Test
    fun parsesDesktopQr() {
        val id = "ab".repeat(32)
        val secret = "cd".repeat(16)
        // As `findex-link` writes it (name percent-encoded).
        val qr = LinkQr.parse("findex://pair?v=1&id=$id&h=192.168.1.5,10.0.0.2&p=47100&s=$secret&n=My%20PC%20%26%20co")
        assertEquals(LinkQr(id, listOf("192.168.1.5", "10.0.0.2"), 47100, secret, "My PC & co"), qr)
        assertNull(LinkQr.parse("https://example.com"))
        assertNull(LinkQr.parse("findex://pair?v=2&id=$id&h=1.2.3.4&p=1&s=$secret"))
        assertNull(LinkQr.parse("findex://pair?v=1&id=abcd&h=1.2.3.4&p=1&s=$secret"))
        assertNull(LinkQr.parse("findex://pair?v=1&id=$id&h=&p=1&s=$secret"))
        assertNull(LinkQr.parse("findex://pair?v=1&id=$id&h=1.2.3.4&p=99999&s=$secret"))
    }
}
