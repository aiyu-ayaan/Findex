package com.aiyu.findex.core.network

import com.aiyu.findex.core.model.StorageItem
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.SSLSocket
import javax.xml.parsers.SAXParserFactory

internal data class WebDavResource(
    val href: String,
    val displayName: String?,
    val isDirectory: Boolean,
    val contentLength: Long,
    val lastModified: Long
)

internal data class PropfindResult(
    val self: WebDavResource?,
    val children: List<WebDavResource>
)

internal object WebDavXml {

    private val PROPFIND_BODY = """
        <?xml version="1.0" encoding="utf-8"?>
        <D:propfind xmlns:D="DAV:">
          <D:prop>
            <D:displayname/>
            <D:resourcetype/>
            <D:getcontentlength/>
            <D:getlastmodified/>
          </D:prop>
        </D:propfind>
    """.trimIndent().toByteArray(Charsets.UTF_8)

    private val HTTP_DATE_NO_DOW = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss z", Locale.US)

    fun propfindPayload(): ByteArray = PROPFIND_BODY

    /**
     * Encodes each segment of a slash-separated path according to RFC 3986 (spaces as `%20`).
     */
    fun encodePath(path: String): String {
        val trimmed = path.trim('/')
        if (trimmed.isEmpty()) return ""
        return trimmed.split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { segment ->
                val raw = decodePath(segment)
                URLEncoder.encode(raw, Charsets.UTF_8.name())
                    .replace("+", "%20")
                    .replace("%21", "!")
                    .replace("%27", "'")
                    .replace("%28", "(")
                    .replace("%29", ")")
                    .replace("%7E", "~")
            }
    }

    fun decodePath(encodedPath: String): String {
        return encodedPath.split('/')
            .joinToString("/") { segment ->
                try {
                    // Preserve literal '+' in URL path segments (only %20 or %2B are percent-encoded in paths).
                    URLDecoder.decode(segment.replace("+", "%2B"), Charsets.UTF_8.name())
                } catch (_: Exception) {
                    segment
                }
            }
    }

    fun parseHttpDate(value: String): Long {
        val text = value.trim()
        if (text.isEmpty()) return 0L
        return try {
            ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        } catch (_: Exception) {
            try {
                // Fallback if day-of-week prefix in RFC 1123 date does not match calendar date
                val withoutDow = text.substringAfter(", ", text).trim()
                ZonedDateTime.parse(withoutDow, HTTP_DATE_NO_DOW).toInstant().toEpochMilli()
            } catch (_: Exception) {
                try {
                    ZonedDateTime.parse(text, DateTimeFormatter.ISO_DATE_TIME).toInstant().toEpochMilli()
                } catch (_: Exception) {
                    0L
                }
            }
        }
    }

    /**
     * Parses a WebDAV `207 Multi-Status` XML stream and partitions entries into [PropfindResult.self]
     * (matching [requestedUrlPath]) and [PropfindResult.children].
     */
    fun parsePropfind(xml: InputStream, requestedUrlPath: String): PropfindResult {
        val responses = mutableListOf<WebDavResource>()
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }

        val handler = object : DefaultHandler() {
            private var inResponse = false
            private var inPropstat = false
            private var inProp = false
            private var inResourceType = false

            private var href = ""
            private var propstatStatus = ""
            private var propDisplayName: String? = null
            private var propIsCollection = false
            private var propContentLength = 0L
            private var propLastModified = 0L

            private var acceptedDisplayName: String? = null
            private var acceptedIsCollection = false
            private var acceptedContentLength = 0L
            private var acceptedLastModified = 0L
            private var hasAcceptedPropstat = false

            private val textBuffer = StringBuilder()

            private fun tagName(localName: String, qName: String): String {
                val base = localName.ifEmpty { qName.substringAfterLast(':') }
                return base.lowercase(Locale.ROOT)
            }

            override fun startElement(uri: String?, localName: String, qName: String, attributes: Attributes?) {
                textBuffer.setLength(0)
                when (tagName(localName, qName)) {
                    "response" -> {
                        inResponse = true
                        href = ""
                        acceptedDisplayName = null
                        acceptedIsCollection = false
                        acceptedContentLength = 0L
                        acceptedLastModified = 0L
                        hasAcceptedPropstat = false
                    }
                    "propstat" -> if (inResponse) {
                        inPropstat = true
                        propstatStatus = ""
                        propDisplayName = null
                        propIsCollection = false
                        propContentLength = 0L
                        propLastModified = 0L
                    }
                    "prop" -> if (inPropstat) {
                        inProp = true
                    }
                    "resourcetype" -> if (inProp) {
                        inResourceType = true
                    }
                    "collection" -> if (inResourceType) {
                        propIsCollection = true
                    }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                textBuffer.append(ch, start, length)
            }

            override fun endElement(uri: String?, localName: String, qName: String) {
                val tag = tagName(localName, qName)
                val value = textBuffer.toString().trim()
                when (tag) {
                    "href" -> if (inResponse && !inPropstat && href.isEmpty()) {
                        href = value
                    }
                    "displayname" -> if (inProp) {
                        propDisplayName = value
                    }
                    "getcontentlength" -> if (inProp) {
                        propContentLength = value.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                    }
                    "getlastmodified" -> if (inProp) {
                        propLastModified = parseHttpDate(value)
                    }
                    "status" -> if (inPropstat && !inProp) {
                        propstatStatus = value
                    }
                    "resourcetype" -> if (inProp) {
                        inResourceType = false
                    }
                    "prop" -> if (inPropstat) {
                        inProp = false
                    }
                    "propstat" -> if (inResponse) {
                        inPropstat = false
                        val isOk = propstatStatus.isEmpty() || propstatStatus.contains(" 200 ") || propstatStatus.endsWith(" 200")
                        if (isOk) {
                            hasAcceptedPropstat = true
                            if (propDisplayName != null) acceptedDisplayName = propDisplayName
                            if (propIsCollection) acceptedIsCollection = true
                            if (propContentLength > 0L) acceptedContentLength = propContentLength
                            if (propLastModified > 0L) acceptedLastModified = propLastModified
                        }
                    }
                    "response" -> if (inResponse) {
                        inResponse = false
                        if (href.isNotEmpty() && (hasAcceptedPropstat || propDisplayName != null || propIsCollection)) {
                            responses += WebDavResource(
                                href = href,
                                displayName = acceptedDisplayName,
                                isDirectory = acceptedIsCollection,
                                contentLength = acceptedContentLength,
                                lastModified = acceptedLastModified
                            )
                        }
                    }
                }
            }
        }

        factory.newSAXParser().parse(InputSource(xml), handler)

        val normTarget = normalizeHrefPath(requestedUrlPath)
        var selfResource: WebDavResource? = null
        val children = mutableListOf<WebDavResource>()

        for (res in responses) {
            val resPath = normalizeHrefPath(res.href)
            if (resPath == normTarget) {
                if (selfResource == null) selfResource = res
            } else {
                children += res
            }
        }
        return PropfindResult(self = selfResource, children = children)
    }

    /** Extracts the decoded path portion of an `href` (which may be a full URI or an absolute path), trimmed of slashes. */
    fun normalizeHrefPath(href: String): String {
        val rawPath = if (href.startsWith("http://", ignoreCase = true) || href.startsWith("https://", ignoreCase = true)) {
            runCatching { URI(href).rawPath ?: href }.getOrElse {
                href.substringAfter("://").substringAfter('/', "")
            }
        } else {
            href
        }
        return decodePath(rawPath).trim('/')
    }

    fun extractName(resource: WebDavResource): String {
        val norm = normalizeHrefPath(resource.href)
        val fromHref = norm.substringAfterLast('/')
        if (fromHref.isNotEmpty()) return fromHref
        return resource.displayName?.trim('/')?.substringAfterLast('/').orEmpty()
    }
}

internal data class RawHttpResponse(val statusCode: Int, val body: ByteArray)

/**
 * Lightweight WebDAV (HTTP / HTTPS) session using [HttpURLConnection] for streaming GET/PUT/DELETE
 * and direct HTTP/1.1 socket transport for WebDAV extension verbs (`PROPFIND`, `MKCOL`, `MOVE`).
 */
internal class WebDavSession(
    private val host: String,
    private val port: Int,
    private val https: Boolean,
    private val basePath: String,
    username: String,
    password: String
) : NetworkSession {

    private val authHeader: String? = if (username.isNotEmpty() || password.isNotEmpty()) {
        val token = Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
        "Basic $token"
    } else {
        null
    }

    override val isConnected: Boolean = true

    fun verify() {
        val url = buildUrl("", trailingSlash = true)
        val response = executeDavVerb(
            url = url,
            method = "PROPFIND",
            headers = mapOf(
                "Depth" to "0",
                "Content-Type" to "application/xml; charset=utf-8"
            ),
            body = WebDavXml.propfindPayload()
        )
        when (response.statusCode) {
            HttpURLConnection.HTTP_OK, 207 -> Unit
            HttpURLConnection.HTTP_UNAUTHORIZED -> throw NetworkException(NetworkError.AUTH_FAILED)
            HttpURLConnection.HTTP_FORBIDDEN -> throw NetworkException(NetworkError.ACCESS_DENIED)
            HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_GONE -> throw NetworkException(NetworkError.SHARE_NOT_FOUND)
            else -> throw mapStatusToException(response.statusCode)
        }
    }

    override fun list(serverId: Long, inner: String): List<StorageItem> {
        val result = propfind(inner, depth = 1, trailingSlash = true)
            ?: throw NetworkException(NetworkError.SHARE_NOT_FOUND)
        val cleanInner = inner.trim('/')
        return result.children.mapNotNull { res ->
            val name = WebDavXml.extractName(res)
            if (name.isEmpty() || name == "." || name == "..") return@mapNotNull null
            val childInner = if (cleanInner.isEmpty()) name else "$cleanInner/$name"
            val isDir = res.isDirectory
            StorageItem(
                path = SmbPath.of(serverId, childInner),
                name = name,
                size = if (isDir) 0L else res.contentLength,
                lastModified = res.lastModified,
                isDirectory = isDir,
                extension = if (isDir) "" else name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            )
        }.sortedWith(compareByDescending<StorageItem> { it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) })
    }

    override fun stat(serverId: Long, inner: String): StorageItem? {
        val cleanInner = inner.trim('/')
        if (cleanInner.isEmpty()) {
            return StorageItem(
                path = SmbPath.of(serverId, ""),
                name = "",
                isDirectory = true
            )
        }
        val result = propfind(cleanInner, depth = 0, trailingSlash = false) ?: return null
        val self = result.self ?: result.children.firstOrNull() ?: return null
        val name = cleanInner.substringAfterLast('/')
        val isDir = self.isDirectory
        return StorageItem(
            path = SmbPath.of(serverId, cleanInner),
            name = name,
            size = if (isDir) 0L else self.contentLength,
            lastModified = self.lastModified,
            isDirectory = isDir,
            extension = if (isDir) "" else name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        )
    }

    override fun mkdir(inner: String) {
        val url = buildUrl(inner, trailingSlash = true)
        val response = executeDavVerb(url, "MKCOL")
        if (response.statusCode !in 200..299 && response.statusCode != HttpURLConnection.HTTP_BAD_METHOD) {
            throw mapStatusToException(response.statusCode)
        }
    }

    override fun delete(inner: String, isDirectory: Boolean) {
        val url = buildUrl(inner, trailingSlash = isDirectory)
        val conn = openStandardConnection(url, "DELETE")
        try {
            val code = conn.responseCode
            if (code !in 200..299 && code != HttpURLConnection.HTTP_NOT_FOUND) {
                throw mapStatusToException(code)
            }
        } finally {
            conn.disconnect()
        }
    }

    override fun rename(inner: String, newInner: String) {
        val srcUrl = buildUrl(inner, trailingSlash = false)
        val destUrl = buildUrl(newInner, trailingSlash = false)
        val response = executeDavVerb(
            url = srcUrl,
            method = "MOVE",
            headers = mapOf(
                "Destination" to destUrl.toExternalForm(),
                "Overwrite" to "F"
            )
        )
        if (response.statusCode !in 200..299) {
            throw mapStatusToException(response.statusCode)
        }
    }

    override suspend fun <T> read(inner: String, block: suspend (InputStream) -> T): T {
        val url = buildUrl(inner, trailingSlash = false)
        val conn = openStandardConnection(url, "GET")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw mapStatusToException(code)
            return conn.inputStream.use { block(it) }
        } finally {
            conn.disconnect()
        }
    }

    override suspend fun <T> write(inner: String, block: suspend (OutputStream) -> T): T {
        val url = buildUrl(inner, trailingSlash = false)
        val conn = openStandardConnection(url, "PUT")
        try {
            conn.doOutput = true
            conn.setChunkedStreamingMode(64 * 1024)
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            val result = conn.outputStream.use { block(it) }
            val code = conn.responseCode
            if (code !in 200..299) throw mapStatusToException(code)
            return result
        } finally {
            conn.disconnect()
        }
    }

    override fun openRandomAccess(
        serverId: Long,
        inner: String,
        knownSize: Long,
        reopenSession: () -> NetworkSession
    ): SmbRandomAccessFile {
        val size = if (knownSize >= 0L) {
            knownSize
        } else {
            stat(serverId, inner)?.size ?: throw NetworkException(NetworkError.SHARE_NOT_FOUND)
        }
        val handle = createRandomAccessHandle(inner, size)
        return SmbRandomAccessFile(
            size = size,
            initial = handle,
            reopen = { createRandomAccessHandle(inner, size) }
        )
    }

    private fun createRandomAccessHandle(inner: String, size: Long): RandomAccessHandle = object : RandomAccessHandle {
        override val size: Long = size

        override fun readChunk(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
            if (length <= 0 || position >= size) return -1
            val endInclusive = minOf(position + length - 1, size - 1)
            val expected = (endInclusive - position + 1).toInt()
            val url = buildUrl(inner, trailingSlash = false)
            val conn = openStandardConnection(url, "GET")
            try {
                conn.setRequestProperty("Range", "bytes=$position-$endInclusive")
                val code = conn.responseCode
                when (code) {
                    HttpURLConnection.HTTP_PARTIAL -> {
                        return conn.inputStream.use { input ->
                            readFullyUpTo(input, buffer, offset, expected)
                        }
                    }
                    HttpURLConnection.HTTP_OK -> {
                        return conn.inputStream.use { input ->
                            skipFully(input, position)
                            readFullyUpTo(input, buffer, offset, expected)
                        }
                    }
                    else -> throw IOException("WebDAV HTTP $code reading range at $position")
                }
            } finally {
                conn.disconnect()
            }
        }

        override fun close() {}
    }

    override fun close() {}

    private fun propfind(inner: String, depth: Int, trailingSlash: Boolean): PropfindResult? {
        val url = buildUrl(inner, trailingSlash = trailingSlash)
        val response = executeDavVerb(
            url = url,
            method = "PROPFIND",
            headers = mapOf(
                "Depth" to depth.toString(),
                "Content-Type" to "application/xml; charset=utf-8"
            ),
            body = WebDavXml.propfindPayload()
        )
        return when (response.statusCode) {
            HttpURLConnection.HTTP_OK, 207 -> {
                WebDavXml.parsePropfind(ByteArrayInputStream(response.body), url.path)
            }
            HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_GONE -> null
            else -> throw mapStatusToException(response.statusCode)
        }
    }

    internal fun buildUrl(inner: String, trailingSlash: Boolean): URL {
        val scheme = if (https) "https" else "http"
        val defaultPort = if (https) 443 else 80
        val portPart = if (port > 0 && port != defaultPort) ":$port" else ""
        val combinedPath = listOf(basePath.trim('/'), inner.trim('/'))
            .filter { it.isNotEmpty() }
            .joinToString("/")
        val encodedPath = WebDavXml.encodePath(combinedPath)
        val suffix = when {
            encodedPath.isEmpty() -> "/"
            trailingSlash -> "/$encodedPath/"
            else -> "/$encodedPath"
        }
        return URL("$scheme://$host$portPart$suffix")
    }

    private fun openStandardConnection(url: URL, method: String): HttpURLConnection {
        return (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            useCaches = false
            requestMethod = method
            authHeader?.let { setRequestProperty("Authorization", it) }
        }
    }

    /**
     * Executes a WebDAV HTTP/1.1 verb (`PROPFIND`, `MKCOL`, `MOVE`) directly over a TCP/TLS socket
     * so custom WebDAV verbs work across all Android versions and JVMs without `HttpURLConnection`
     * method-whitelist restrictions.
     */
    private fun executeDavVerb(
        url: URL,
        method: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        redirectsRemaining: Int = 5
    ): RawHttpResponse {
        val isHttps = url.protocol.equals("https", ignoreCase = true)
        val defaultPort = if (isHttps) 443 else 80
        val targetPort = if (url.port > 0) url.port else defaultPort

        val rawSocket = Socket()
        rawSocket.soTimeout = 30_000
        rawSocket.connect(InetSocketAddress(url.host, targetPort), 15_000)

        val socket: Socket = if (isHttps) {
            val ssl = HttpsURLConnection.getDefaultSSLSocketFactory()
                .createSocket(rawSocket, url.host, targetPort, true) as SSLSocket
            ssl.soTimeout = 30_000
            ssl.startHandshake()
            if (!HttpsURLConnection.getDefaultHostnameVerifier().verify(url.host, ssl.session)) {
                runCatching { ssl.close() }
                throw SSLPeerUnverifiedException("Cannot verify hostname: ${url.host}")
            }
            ssl
        } else {
            rawSocket
        }

        socket.use { sock ->
            val out = sock.getOutputStream()
            val pathAndQuery = buildString {
                append(url.path.ifEmpty { "/" })
                if (!url.query.isNullOrEmpty()) append('?').append(url.query)
            }
            val hostHeader = if (targetPort == defaultPort) url.host else "${url.host}:$targetPort"
            val req = StringBuilder().apply {
                append("$method $pathAndQuery HTTP/1.1\r\n")
                append("Host: $hostHeader\r\n")
                append("User-Agent: Findex/1.0\r\n")
                append("Accept: */*\r\n")
                append("Connection: close\r\n")
                authHeader?.let { append("Authorization: $it\r\n") }
                for ((k, v) in headers) {
                    append("$k: $v\r\n")
                }
                append("Content-Length: ${body?.size ?: 0}\r\n")
                append("\r\n")
            }
            out.write(req.toString().toByteArray(Charsets.ISO_8859_1))
            if (body != null && body.isNotEmpty()) {
                out.write(body)
            }
            out.flush()

            val input = sock.getInputStream()
            val statusLine = readAsciiLine(input) ?: throw EOFException("Empty HTTP response from ${url.host}")
            val parts = statusLine.split(' ', limit = 3)
            val statusCode = parts.getOrNull(1)?.toIntOrNull()
                ?: throw IOException("Malformed HTTP status line: $statusLine")

            val respHeaders = mutableMapOf<String, String>()
            while (true) {
                val line = readAsciiLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val key = line.substring(0, colon).trim().lowercase(Locale.ROOT)
                    val value = line.substring(colon + 1).trim()
                    respHeaders[key] = value
                }
            }

            if (statusCode in intArrayOf(301, 302, 307, 308) && redirectsRemaining > 0) {
                val location = respHeaders["location"]
                if (!location.isNullOrEmpty()) {
                    val nextUrl = URL(url, location)
                    return executeDavVerb(nextUrl, method, headers, body, redirectsRemaining - 1)
                }
            }

            val transferEncoding = respHeaders["transfer-encoding"].orEmpty().lowercase(Locale.ROOT)
            val contentLength = respHeaders["content-length"]?.toIntOrNull() ?: -1
            val responseBody = when {
                "chunked" in transferEncoding -> readChunkedBody(input)
                contentLength == 0 -> ByteArray(0)
                contentLength > 0 -> readExactBytes(input, contentLength)
                else -> input.readBytes()
            }
            return RawHttpResponse(statusCode, responseBody)
        }
    }

    private fun readAsciiLine(input: InputStream): String? {
        val sb = StringBuilder()
        var sawAny = false
        while (true) {
            val b = input.read()
            if (b < 0) return if (sawAny) sb.toString() else null
            sawAny = true
            if (b == '\n'.code) break
            if (b != '\r'.code) sb.append(b.toChar())
        }
        return sb.toString()
    }

    private fun readChunkedBody(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val sizeLine = readAsciiLine(input)?.substringBefore(';')?.trim().orEmpty()
            if (sizeLine.isEmpty()) break
            val chunkSize = sizeLine.toIntOrNull(16) ?: break
            if (chunkSize == 0) {
                readAsciiLine(input)
                break
            }
            out.write(readExactBytes(input, chunkSize))
            readAsciiLine(input) // trailing CRLF after chunk
        }
        return out.toByteArray()
    }

    private fun readExactBytes(input: InputStream, count: Int): ByteArray {
        val buf = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val n = input.read(buf, offset, count - offset)
            if (n < 0) break
            offset += n
        }
        return if (offset == count) buf else buf.copyOf(offset)
    }

    private fun mapStatusToException(code: Int): NetworkException = when (code) {
        HttpURLConnection.HTTP_UNAUTHORIZED -> NetworkException(NetworkError.AUTH_FAILED)
        HttpURLConnection.HTTP_FORBIDDEN -> NetworkException(NetworkError.ACCESS_DENIED)
        HttpURLConnection.HTTP_NOT_FOUND, HttpURLConnection.HTTP_GONE -> NetworkException(NetworkError.SHARE_NOT_FOUND)
        else -> NetworkException(NetworkError.UNKNOWN)
    }
}

internal fun readFullyUpTo(input: InputStream, buffer: ByteArray, offset: Int, length: Int): Int {
    var total = 0
    while (total < length) {
        val n = input.read(buffer, offset + total, length - total)
        if (n < 0) break
        total += n
    }
    return if (total == 0) -1 else total
}

internal fun skipFully(input: InputStream, bytesToSkip: Long) {
    var remaining = bytesToSkip
    val discard = ByteArray(16 * 1024)
    while (remaining > 0L) {
        val skipped = input.skip(remaining)
        if (skipped > 0L) {
            remaining -= skipped
        } else {
            val toRead = minOf(discard.size.toLong(), remaining).toInt()
            val n = input.read(discard, 0, toRead)
            if (n < 0) break
            remaining -= n
        }
    }
}
