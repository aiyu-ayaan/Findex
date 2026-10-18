package com.aiyu.findex.core.viewer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import javax.inject.Inject
import javax.inject.Singleton

class NotTextException : Exception("binary file")

data class TextDocument(
    val text: String,
    val charset: Charset,
    val bom: Boolean,
    val lineEnding: String,
    val lastModified: Long,
    val truncated: Boolean,
    val readOnly: Boolean
)

sealed interface SaveResult {
    data object Saved : SaveResult
    /** The file changed on disk since it was loaded. */
    data object Conflict : SaveResult
    data class Failed(val error: Throwable) : SaveResult
}

/** Local text files only; network files open in an external app. */
@Singleton
class TextDocuments @Inject constructor() {

    suspend fun load(path: String, editLimit: Long = EDIT_LIMIT, viewLimit: Long = VIEW_LIMIT): TextDocument = withContext(Dispatchers.IO) {
        val file = File(path)
        val length = file.length()
        val bytes = file.inputStream().use { it.readNBytes(minOf(length, viewLimit).toInt()) }
        if (bytes.take(BINARY_SCAN).any { it == 0.toByte() }) throw NotTextException()
        val bom = bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        val body = if (bom) bytes.copyOfRange(3, bytes.size) else bytes
        val charset = detectCharset(body, cut = length > viewLimit)
        val raw = String(body, charset)
        val ending = if ("\r\n" in raw) "\r\n" else "\n"
        TextDocument(
            text = if (ending == "\r\n") raw.replace("\r\n", "\n") else raw,
            charset = charset, bom = bom, lineEnding = ending,
            lastModified = file.lastModified(),
            truncated = length > viewLimit,
            readOnly = length > editLimit
        )
    }

    /** Writes next to the target and renames over it, so a crash never leaves a half-written file. */
    suspend fun save(path: String, text: String, base: TextDocument, force: Boolean = false): SaveResult = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!force && file.lastModified() != base.lastModified) return@withContext SaveResult.Conflict
        val tmp = File(file.parentFile, ".${file.name}.fdxtmp")
        try {
            val out = if (base.lineEnding == "\r\n") text.replace("\n", "\r\n") else text
            tmp.outputStream().use { s ->
                if (base.bom) s.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                s.write(out.toByteArray(base.charset))
                s.fd.sync()
            }
            if (!tmp.renameTo(file)) throw java.io.IOException("rename failed")
            SaveResult.Saved
        } catch (e: Exception) {
            tmp.delete()
            SaveResult.Failed(e)
        }
    }

    companion object {
        const val EDIT_LIMIT = 2L * 1024 * 1024
        const val VIEW_LIMIT = 8L * 1024 * 1024
        private const val BINARY_SCAN = 8192

        // Strict UTF-8 first, else Latin-1 (decodes every byte).
        internal fun detectCharset(bytes: ByteArray, cut: Boolean = false): Charset {
            // When the read was cut, up to 3 trailing bytes may be a multi-byte character cut by the size limit.
            for (drop in 0..(if (cut) 3 else 0)) {
                if (bytes.size < drop) break
                val d = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                try { d.decode(ByteBuffer.wrap(bytes, 0, bytes.size - drop)); return Charsets.UTF_8 } catch (_: CharacterCodingException) {}
            }
            return Charsets.ISO_8859_1
        }
    }
}

