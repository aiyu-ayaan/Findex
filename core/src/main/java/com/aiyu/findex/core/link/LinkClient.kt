package com.aiyu.findex.core.link

import com.aiyu.findex.core.network.NetworkError
import com.aiyu.findex.core.network.NetworkException
import org.json.JSONObject
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

/** One file or folder as the Link API returns it. [mtime] is Unix milliseconds. */
data class LinkItem(val name: String, val dir: Boolean, val size: Long, val mtime: Long)

/** A storage root of a device, with capacity when known (0 = unknown). */
data class LinkRoot(val name: String, val total: Long, val free: Long)

data class LinkInfo(val id: String, val name: String, val platform: String, val version: String, val roots: List<LinkRoot>)

/**
 * HTTPS client for a Findex Link server (desktop or another phone). [pin] is the server certificate
 * fingerprint (null only while pairing); [token] is the bearer token the server issued at pairing.
 * Blocking: call it from [kotlinx.coroutines.Dispatchers.IO].
 */
class LinkClient(
    private val host: String,
    private val port: Int,
    private val pin: ByteArray?,
    private val token: String?
) {
    /** Fingerprint of the certificate the server showed on the last connection. */
    @Volatile var seen: ByteArray? = null
        private set

    private val sockets: SSLSocketFactory = LinkIdentity.clientSocketFactory(pin) { seen = it }

    internal fun connect(method: String, target: String, readTimeoutMs: Int = 30_000): HttpsURLConnection {
        val url = URL("https", host, port, target)
        return (url.openConnection() as HttpsURLConnection).apply {
            sslSocketFactory = sockets
            // The pinned fingerprint identifies the server; certificates carry no host name to check.
            hostnameVerifier = HostnameVerifier { _, _ -> true }
            requestMethod = method
            connectTimeout = 5_000
            readTimeout = readTimeoutMs
            useCaches = false
            instanceFollowRedirects = false
            setRequestProperty("Connection", "close")
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
    }

    private fun HttpsURLConnection.json(): JSONObject {
        val code = try { responseCode } catch (e: IOException) { throw mapIo(e) }
        val stream = if (code in 200..299) inputStream else errorStream
        val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
        disconnect()
        val body = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
        if (code !in 200..299) throw LinkHttpException(code, body.optString("error", "error"))
        return body
    }

    private fun call(method: String, route: String, path: String? = null, extra: String = "", body: ByteArray? = null, readTimeoutMs: Int = 30_000): JSONObject {
        val query = path?.let { "?path=${enc(it)}$extra" }.orEmpty()
        val conn = connect(method, route + query, readTimeoutMs)
        return try {
            if (body != null) {
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(body.size)
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body) }
            } else if (method != "GET") {
                conn.doOutput = true
                conn.setFixedLengthStreamingMode(0)
                conn.outputStream.close()
            }
            conn.json()
        } catch (e: IOException) {
            conn.disconnect()
            throw mapIo(e)
        }
    }

    fun info(): LinkInfo {
        val v = call("GET", "/v1/info")
        val roots = v.optJSONArray("roots")
        return LinkInfo(
            id = v.optString("id"), name = v.optString("name"), platform = v.optString("platform"), version = v.optString("version"),
            roots = (0 until (roots?.length() ?: 0)).map { i ->
                roots!!.getJSONObject(i).let { LinkRoot(it.optString("name"), it.optLong("total"), it.optLong("free")) }
            }
        )
    }

    fun list(path: String): List<LinkItem> {
        val items = call("GET", "/v1/list", path).optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).map { item(items.getJSONObject(it)) }
    }

    /** Null when nothing is at [path]. */
    fun stat(path: String): LinkItem? = try {
        item(call("GET", "/v1/stat", path))
    } catch (e: LinkHttpException) {
        if (e.status == 404) null else throw e
    }

    /** File contents from [offset] (a range request, so media can seek). The caller closes the stream. */
    fun open(path: String, offset: Long = 0): InputStream {
        val conn = connect("GET", "/v1/file?path=${enc(path)}")
        if (offset > 0) conn.setRequestProperty("Range", "bytes=$offset-")
        try {
            val code = conn.responseCode
            if (code != 200 && code != 206) conn.json()
            val input = conn.inputStream
            // A server that ignores Range would send the whole file; skip to the offset then.
            if (code == 200 && offset > 0) com.aiyu.findex.core.network.skipFully(input, offset)
            return object : FilterInputStream(input) {
                override fun close() { runCatching { super.close() }; conn.disconnect() }
            }
        } catch (e: IOException) {
            conn.disconnect()
            throw mapIo(e)
        }
    }

    /** Creates or replaces a file; [block] writes the contents (length unknown: chunked). */
    fun <T> upload(path: String, block: (OutputStream) -> T): T {
        val conn = connect("PUT", "/v1/file?path=${enc(path)}", readTimeoutMs = 120_000)
        try {
            conn.doOutput = true
            conn.setChunkedStreamingMode(64 * 1024)
            val result = conn.outputStream.use(block)
            conn.json()
            return result
        } catch (e: IOException) {
            conn.disconnect()
            throw mapIo(e)
        }
    }

    fun mkdir(path: String) { call("POST", "/v1/mkdir", path) }

    /** To the computer's recycle bin, never permanent. */
    fun delete(path: String) { call("POST", "/v1/delete", path) }

    fun rename(path: String, to: String) { call("POST", "/v1/rename", path, "&to=${enc(to)}") }

    /** Pushes text: the computer copies it to its clipboard. */
    fun sendText(text: String) {
        val conn = connect("POST", "/v1/text")
        try {
            val bytes = text.toByteArray(Charsets.UTF_8)
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
            conn.json()
        } catch (e: IOException) {
            conn.disconnect()
            throw mapIo(e)
        }
    }

    fun unpair() { call("POST", "/v1/unpair") }

    internal fun pairStart(body: JSONObject): JSONObject = call("POST", "/v1/pair/start", body = body.toString().toByteArray())

    /** Waits for the user on the other device (up to 90 s). */
    internal fun pairFinish(body: JSONObject): JSONObject =
        call("POST", "/v1/pair/finish", body = body.toString().toByteArray(), readTimeoutMs = 90_000)

    private fun item(o: JSONObject) = LinkItem(o.optString("name"), o.optBoolean("dir"), o.optLong("size"), o.optLong("mtime"))

    companion object {
        fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        internal fun mapIo(e: IOException): Exception = when (e) {
            is LinkHttpException -> e
            is javax.net.ssl.SSLException -> NetworkException(NetworkError.AUTH_FAILED, e)
            else -> NetworkException(NetworkError.HOST_UNREACHABLE, e)
        }
    }
}

/** An error answer from the server, `{"error": code}` with an HTTP status. */
class LinkHttpException(val status: Int, val code: String) : IOException("$status $code") {
    fun toNetwork(): NetworkException = NetworkException(
        when (status) {
            HttpURLConnection.HTTP_UNAUTHORIZED -> NetworkError.AUTH_FAILED
            HttpURLConnection.HTTP_FORBIDDEN -> NetworkError.ACCESS_DENIED
            HttpURLConnection.HTTP_NOT_FOUND -> NetworkError.SHARE_NOT_FOUND
            else -> NetworkError.UNKNOWN
        },
        this
    )
}
