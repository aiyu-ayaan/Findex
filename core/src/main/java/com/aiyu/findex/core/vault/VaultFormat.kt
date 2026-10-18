package com.aiyu.findex.core.vault

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Plaintext metadata stored encrypted inside every blob header. */
data class VaultMeta(val name: String, val mime: String, val size: Long, val mtime: Long, val originalPath: String)

/** A blob or key that fails authentication: wrong key, tampering, or truncation. */
class VaultCorruptException(cause: Throwable? = null) : IOException("vault data failed authentication", cause)

/**
 * Pure-JVM crypto for the vault (see NEXT_FEATURES_DESIGN.md §2.2). Blob `.fdxv` v1:
 * `"FDXV" | ver | log2(chunk) | fileSalt16 | noncePrefix7 | metaLen u32 | meta | chunks`,
 * each part AES-256-GCM with the 33-byte header as AAD and nonce `prefix || counter || lastFlag`.
 */
object VaultFormat {
    const val VERSION: Byte = 1
    const val MIN_ITERATIONS = 310_000
    private const val CHUNK_LOG2 = 16
    const val CHUNK = 1 shl CHUNK_LOG2
    private const val TAG = 16
    const val HEADER = 33
    private val MAGIC = byteArrayOf('F'.code.toByte(), 'D'.code.toByte(), 'X'.code.toByte(), 'V'.code.toByte())
    private val KEY_MAGIC = byteArrayOf('F'.code.toByte(), 'D'.code.toByte(), 'X'.code.toByte(), 'K'.code.toByte())
    private const val FILE_INFO = "findex-vault-file-v1"
    private const val INDEX_INFO = "findex-vault-index-v1"
    private const val META_COUNTER = -1 // 0xFFFFFFFF
    private const val META_FLAG = 2

    private val random = SecureRandom()

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { random.nextBytes(it) }

    // region Keys

    fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** Iterations that take about [targetMs] on this device, never below [MIN_ITERATIONS]. */
    fun calibrateIterations(targetMs: Long = 600): Int {
        val probe = 20_000
        val start = System.nanoTime()
        pbkdf2(charArrayOf('x'), ByteArray(16), probe)
        val ms = ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1)
        return (probe * targetMs / ms).coerceIn(MIN_ITERATIONS.toLong(), 5_000_000L).toInt()
    }

    fun hkdf(ikm: ByteArray, salt: ByteArray, info: String, length: Int = 32): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(32) else salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val out = ByteArrayOutputStream()
        var t = ByteArray(0)
        var i = 1
        while (out.size() < length) {
            mac.update(t); mac.update(info.toByteArray()); mac.update(i.toByte())
            t = mac.doFinal(); out.write(t); i++
        }
        prk.fill(0)
        return out.toByteArray().copyOf(length)
    }

    fun indexKey(dek: ByteArray): ByteArray = hkdf(dek, ByteArray(0), INDEX_INFO)

    /** `iv12 || AES-GCM(key, plain)`. */
    fun seal(key: ByteArray, plain: ByteArray): ByteArray {
        val iv = randomBytes(12)
        val c = gcm(Cipher.ENCRYPT_MODE, key, iv)
        return iv + c.doFinal(plain)
    }

    fun open(key: ByteArray, sealed: ByteArray): ByteArray {
        if (sealed.size < 12 + TAG) throw VaultCorruptException()
        return try {
            gcm(Cipher.DECRYPT_MODE, key, sealed.copyOfRange(0, 12)).doFinal(sealed, 12, sealed.size - 12)
        } catch (e: GeneralSecurityException) {
            throw VaultCorruptException(e)
        }
    }

    fun sealString(key: ByteArray, text: String): String = b64(seal(key, text.toByteArray()))
    fun openString(key: ByteArray, b64: String): String = String(open(key, unb64(b64)))

    fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun unb64(text: String): ByteArray = Base64.getDecoder().decode(text)

    /** `vault.key`: `"FDXK" | version | iterations u32 | salt16 | passwordWrappedKey`. */
    data class KeyFile(val iterations: Int, val salt: ByteArray, val wrapped: ByteArray)

    fun writeKeyFile(out: OutputStream, key: KeyFile) {
        val d = DataOutputStream(out)
        d.write(KEY_MAGIC); d.writeByte(VERSION.toInt()); d.writeInt(key.iterations); d.write(key.salt); d.write(key.wrapped)
        d.flush()
    }

    fun readKeyFile(input: InputStream): KeyFile {
        val d = DataInputStream(input)
        val magic = ByteArray(4).also { d.readFully(it) }
        if (!magic.contentEquals(KEY_MAGIC) || d.readByte() != VERSION) throw VaultCorruptException()
        val iterations = d.readInt()
        val salt = ByteArray(16).also { d.readFully(it) }
        return KeyFile(iterations, salt, d.readBytes())
    }

    // endregion

    // region Blobs

    fun encrypt(input: InputStream, output: OutputStream, dek: ByteArray, meta: VaultMeta, onChunk: (Long) -> Unit) {
        val fileSalt = randomBytes(16)
        val prefix = randomBytes(7)
        val fileKey = hkdf(dek, fileSalt, FILE_INFO)
        try {
            val metaPlain = ByteArrayOutputStream().also { bo ->
                DataOutputStream(bo).run { writeUTF(meta.name); writeUTF(meta.mime); writeLong(meta.size); writeLong(meta.mtime); writeUTF(meta.originalPath) }
            }.toByteArray()
            val metaLen = metaPlain.size + TAG
            val header = ByteBuffer.allocate(HEADER).put(MAGIC).put(VERSION).put(CHUNK_LOG2.toByte())
                .put(fileSalt).put(prefix).putInt(metaLen).array()
            output.write(header)
            output.write(gcm(Cipher.ENCRYPT_MODE, fileKey, nonce(prefix, META_COUNTER, META_FLAG)).apply { updateAAD(header) }.doFinal(metaPlain))

            // Read one chunk ahead so the final chunk can carry the "last" flag.
            var cur = ByteArray(CHUNK)
            var next = ByteArray(CHUNK)
            var curLen = readFull(input, cur)
            var index = 0
            while (true) {
                val nextLen = if (curLen == CHUNK) readFull(input, next) else 0
                val last = nextLen == 0
                val c = gcm(Cipher.ENCRYPT_MODE, fileKey, nonce(prefix, index, if (last) 1 else 0)).apply { updateAAD(header) }
                output.write(c.doFinal(cur, 0, curLen))
                onChunk(curLen.toLong())
                if (last) break
                index++
                val t = cur; cur = next; next = t; curLen = nextLen
            }
            cur.fill(0); next.fill(0)
            output.flush()
        } finally {
            fileKey.fill(0)
        }
    }

    internal class Header(val bytes: ByteArray, val fileKey: ByteArray, val prefix: ByteArray, val metaLen: Int)

    private fun readHeader(d: DataInputStream, dek: ByteArray): Header {
        val header = ByteArray(HEADER)
        try { d.readFully(header) } catch (e: EOFException) { throw VaultCorruptException(e) }
        if (!header.copyOfRange(0, 4).contentEquals(MAGIC) || header[4] != VERSION || header[5].toInt() != CHUNK_LOG2) throw VaultCorruptException()
        val salt = header.copyOfRange(6, 22)
        val prefix = header.copyOfRange(22, 29)
        val metaLen = ByteBuffer.wrap(header, 29, 4).int
        if (metaLen < TAG || metaLen > 4 * 65536) throw VaultCorruptException()
        return Header(header, hkdf(dek, salt, FILE_INFO), prefix, metaLen)
    }

    private fun readMetaBody(d: DataInputStream, h: Header): VaultMeta {
        val enc = ByteArray(h.metaLen)
        try { d.readFully(enc) } catch (e: EOFException) { throw VaultCorruptException(e) }
        val plain = decryptPart(h, META_COUNTER, META_FLAG, enc, enc.size)
        return DataInputStream(plain.inputStream()).run { VaultMeta(readUTF(), readUTF(), readLong(), readLong(), readUTF()) }
    }

    private fun decryptPart(h: Header, counter: Int, flag: Int, buf: ByteArray, len: Int): ByteArray = try {
        gcm(Cipher.DECRYPT_MODE, h.fileKey, nonce(h.prefix, counter, flag)).apply { updateAAD(h.bytes) }.doFinal(buf, 0, len)
    } catch (e: AEADBadTagException) {
        throw VaultCorruptException(e)
    } catch (e: GeneralSecurityException) {
        throw VaultCorruptException(e)
    }

    fun readMeta(input: InputStream, dek: ByteArray): VaultMeta {
        val d = DataInputStream(input)
        val h = readHeader(d, dek)
        try { return readMetaBody(d, h) } finally { h.fileKey.fill(0) }
    }

    fun decrypt(input: InputStream, output: OutputStream, dek: ByteArray, onChunk: (Long) -> Unit): VaultMeta {
        val d = DataInputStream(input)
        val h = readHeader(d, dek)
        try {
            val meta = readMetaBody(d, h)
            var cur = ByteArray(CHUNK + TAG)
            var next = ByteArray(CHUNK + TAG)
            var curLen = readFull(d, cur)
            var index = 0
            while (true) {
                val nextLen = if (curLen == cur.size) readFull(d, next) else 0
                val last = nextLen == 0
                if (curLen < TAG) throw VaultCorruptException()
                val plain = decryptPart(h, index, if (last) 1 else 0, cur, curLen)
                output.write(plain)
                onChunk(plain.size.toLong())
                plain.fill(0)
                if (last) break
                index++
                val t = cur; cur = next; next = t; curLen = nextLen
            }
            output.flush()
            return meta
        } finally {
            h.fileKey.fill(0)
        }
    }

    /** Seekable plaintext view of a blob, for streaming to viewers. Methods are synchronized. */
    class VaultRandomAccess internal constructor(
        private val file: RandomAccessFile,
        private val h: Header,
        val meta: VaultMeta,
        private val dataStart: Long,
        private val chunkCount: Long,
        val size: Long
    ) : AutoCloseable {
        private val enc = ByteArray(CHUNK + TAG)
        private var cachedIndex = -1L
        private var cached = ByteArray(0)

        @Synchronized
        fun readAt(pos: Long, buf: ByteArray, off: Int, len: Int): Int {
            if (pos >= size) return -1
            var done = 0
            var p = pos
            while (done < len && p < size) {
                val index = p / CHUNK
                if (index != cachedIndex) load(index)
                val inChunk = (p - index * CHUNK).toInt()
                val n = minOf(len - done, cached.size - inChunk)
                System.arraycopy(cached, inChunk, buf, off + done, n)
                done += n; p += n
            }
            return done
        }

        private fun load(index: Long) {
            val start = dataStart + index * (CHUNK + TAG)
            val len = minOf((CHUNK + TAG).toLong(), file.length() - start).toInt()
            file.seek(start)
            file.readFully(enc, 0, len)
            cached.fill(0)
            cached = decryptPart(h, index.toInt(), if (index == chunkCount - 1) 1 else 0, enc, len)
            cachedIndex = index
        }

        @Synchronized
        override fun close() {
            cached.fill(0); h.fileKey.fill(0); file.close()
        }
    }

    fun openRandomAccess(file: RandomAccessFile, dek: ByteArray): VaultRandomAccess {
        file.seek(0)
        val d = DataInputStream(object : InputStream() {
            override fun read(): Int = file.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = file.read(b, off, len)
        })
        val h = readHeader(d, dek)
        val meta = readMetaBody(d, h)
        val dataStart = HEADER.toLong() + h.metaLen
        val dataLen = file.length() - dataStart
        if (dataLen < TAG) throw VaultCorruptException()
        val chunks = (dataLen + CHUNK + TAG - 1) / (CHUNK + TAG)
        val size = dataLen - chunks * TAG
        if (size < 0) throw VaultCorruptException()
        return VaultRandomAccess(file, h, meta, dataStart, chunks, size)
    }

    // endregion

    private fun gcm(mode: Int, key: ByteArray, iv: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv)) }

    private fun nonce(prefix: ByteArray, counter: Int, flag: Int): ByteArray =
        ByteBuffer.allocate(12).put(prefix).putInt(counter).put(flag.toByte()).array()

    private fun readFull(input: InputStream, buf: ByteArray): Int {
        var n = 0
        while (n < buf.size) {
            val r = input.read(buf, n, buf.size - n)
            if (r < 0) break
            n += r
        }
        return n
    }
}
