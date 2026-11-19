package com.aiyu.findex.core.link

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Fingerprints, nonces, tokens and the 6-digit pairing code of Findex Link.
 * Must match `findex-link/src/sas.rs` in the desktop app byte for byte (shared test vector in `LinkCryptoTest`).
 */
object LinkCrypto {
    private val random = SecureRandom()

    fun sha256(vararg parts: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").apply { parts.forEach { update(it) } }.digest()

    fun random32(): ByteArray = ByteArray(32).also { random.nextBytes(it) }

    /** A fresh 256-bit bearer token, hex encoded. */
    fun token(): String = hex(random32())

    /**
     * `SHA-256("findex-sas-v1" || server fp || client fp || client nonce || server nonce)`,
     * first 4 bytes big-endian, mod 10^6, zero padded. The client commits to its nonce first,
     * so a man in the middle cannot search for matching codes.
     */
    fun code(fpServer: ByteArray, fpClient: ByteArray, nonceClient: ByteArray, nonceServer: ByteArray): String {
        val h = sha256("findex-sas-v1".toByteArray(), fpServer, fpClient, nonceClient, nonceServer)
        val n = (ByteBuffer.wrap(h, 0, 4).int.toLong() and 0xffffffffL) % 1_000_000
        return "%06d".format(n)
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun unhex(s: String): ByteArray? {
        if (s.length % 2 != 0) return null
        return ByteArray(s.length / 2) { i ->
            s.substring(i * 2, i * 2 + 2).toIntOrNull(16)?.toByte() ?: return null
        }
    }

    /** Constant-time comparison for tokens. */
    fun same(a: String, b: String): Boolean = MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
}
