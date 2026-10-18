package com.aiyu.findex.core.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.ServerSocket
import kotlin.concurrent.thread

class NetworkProtocolsTest {

    @Test
    fun `protocol default ports and fromName`() {
        assertEquals(445, NetworkProtocol.SMB.defaultPort)
        assertEquals(22, NetworkProtocol.SFTP.defaultPort)
        assertEquals(21, NetworkProtocol.FTP.defaultPort)
        assertEquals(80, NetworkProtocol.WEBDAV.defaultPort)
        assertEquals(443, NetworkProtocol.WEBDAVS.defaultPort)

        assertEquals(NetworkProtocol.SFTP, NetworkProtocol.fromName("sftp"))
        assertEquals(NetworkProtocol.WEBDAVS, NetworkProtocol.fromName("WEBDAVS"))
        assertEquals(NetworkProtocol.SMB, NetworkProtocol.fromName("unknown"))
    }

    @Test
    fun `endpoint normalization extracts scheme port and path`() {
        val webdav = NetworkEndpoint.normalize(
            rawHost = "https://cloud.example.com:5006/remote.php/dav",
            rawShare = "/files/aiyu/",
            protocol = NetworkProtocol.WEBDAV,
            rawPort = 0
        )
        assertEquals("cloud.example.com", webdav.host)
        assertEquals("remote.php/dav/files/aiyu", webdav.share)
        assertEquals(NetworkProtocol.WEBDAVS, webdav.protocol)
        assertEquals(5006, webdav.port)

        val sftpDefaultPort = NetworkEndpoint.normalize(
            rawHost = "sftp://192.168.1.50:22",
            rawShare = "/mnt/media",
            protocol = NetworkProtocol.SFTP,
            rawPort = 22
        )
        assertEquals("192.168.1.50", sftpDefaultPort.host)
        assertEquals("mnt/media", sftpDefaultPort.share)
        assertEquals(NetworkProtocol.SFTP, sftpDefaultPort.protocol)
        assertEquals(0, sftpDefaultPort.port) // 22 is default for SFTP

        val smbUnc = NetworkEndpoint.normalize(
            rawHost = "\\\\nas.local\\Public",
            rawShare = "Movies",
            protocol = NetworkProtocol.SMB,
            rawPort = 0
        )
        assertEquals("nas.local", smbUnc.host)
        assertEquals("Public/Movies", smbUnc.share)
        assertEquals(NetworkProtocol.SMB, smbUnc.protocol)
    }

    @Test
    fun `NetworkServer displayAddress and label format per protocol`() {
        val smb = NetworkServer(1, "", "192.168.1.10", "Public", "aiyu", "", NetworkProtocol.SMB, 0)
        assertEquals("192.168.1.10/Public", smb.label)
        assertEquals("\\\\192.168.1.10\\Public", smb.displayAddress)

        val sftp = NetworkServer(2, "Home NAS", "nas.local", "media/videos", "root", "", NetworkProtocol.SFTP, 2222)
        assertEquals("Home NAS", sftp.label)
        assertEquals("sftp://nas.local:2222/media/videos", sftp.displayAddress)

        val ftpRoot = NetworkServer(3, "", "ftp.local", "", "", "", NetworkProtocol.FTP, 0)
        assertEquals("ftp.local", ftpRoot.label)
        assertEquals("ftp://ftp.local", ftpRoot.displayAddress)

        val webdavs = NetworkServer(4, "Nextcloud", "cloud.local", "remote.php/dav/files/aiyu", "aiyu", "", NetworkProtocol.WEBDAVS, 0)
        assertEquals("https://cloud.local/remote.php/dav/files/aiyu", webdavs.displayAddress)
    }

    @Test
    fun `WebDavXml path encoding and decoding preserves special characters`() {
        val raw = "folder name/my+file (2026).mp4"
        val encoded = WebDavXml.encodePath(raw)
        assertEquals("folder%20name/my%2Bfile%20(2026).mp4", encoded)
        assertEquals(raw, WebDavXml.decodePath(encoded))
    }

    @Test
    fun `WebDavXml parsePropfind separates self and children and ignores 404 propstats`() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/remote.php/dav/files/aiyu/Photos/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:displayname>Photos</d:displayname>
                    <d:resourcetype><d:collection/></d:resourcetype>
                    <d:getlastmodified>Sun, 18 Oct 2026 10:00:00 GMT</d:getlastmodified>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>https://cloud.local/remote.php/dav/files/aiyu/Photos/Vacation%202026/</d:href>
                <d:propstat>
                  <d:prop>
                    <d:displayname>Vacation 2026</d:displayname>
                    <d:resourcetype><d:collection/></d:resourcetype>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
                <d:propstat>
                  <d:prop>
                    <d:getcontentlength/>
                  </d:prop>
                  <d:status>HTTP/1.1 404 Not Found</d:status>
                </d:propstat>
              </d:response>
              <d:response>
                <d:href>/remote.php/dav/files/aiyu/Photos/sunset%2Bbeach.jpg</d:href>
                <d:propstat>
                  <d:prop>
                    <d:displayname>sunset+beach.jpg</d:displayname>
                    <d:resourcetype/>
                    <d:getcontentlength>4194304</d:getcontentlength>
                    <d:getlastmodified>Sun, 18 Oct 2026 10:30:00 GMT</d:getlastmodified>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val result = WebDavXml.parsePropfind(
            ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)),
            "/remote.php/dav/files/aiyu/Photos/"
        )

        assertNotNull(result.self)
        assertTrue(result.self!!.isDirectory)
        assertEquals(2, result.children.size)

        val folder = result.children[0]
        assertTrue(folder.isDirectory)
        assertEquals("Vacation 2026", WebDavXml.extractName(folder))

        val file = result.children[1]
        assertFalse(file.isDirectory)
        assertEquals("sunset+beach.jpg", WebDavXml.extractName(file))
        assertEquals(4194304L, file.contentLength)
        assertTrue(file.lastModified > 0L)
    }

    @Test
    fun `WebDavSession executes PROPFIND MKCOL and MOVE over loopback socket`() {
        val propfindXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/dav/</D:href>
                <D:propstat>
                  <D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
              <D:response>
                <D:href>/dav/hello.txt</D:href>
                <D:propstat>
                  <D:prop>
                    <D:resourcetype/>
                    <D:getcontentlength>12</D:getcontentlength>
                  </D:prop>
                  <D:status>HTTP/1.1 200 OK</D:status>
                </D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val seenMethods = mutableListOf<String>()
        ServerSocket(0).use { server ->
            val port = server.localPort
            val serverThread = thread {
                repeat(3) {
                    server.accept().use { client ->
                        val reader = client.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                        val requestLine = reader.readLine().orEmpty()
                        val method = requestLine.substringBefore(' ')
                        seenMethods += method
                        while (true) {
                            val h = reader.readLine() ?: break
                            if (h.isEmpty()) break
                        }
                        val out = client.getOutputStream()
                        when (method) {
                            "PROPFIND" -> {
                                out.write("HTTP/1.1 207 Multi-Status\r\nContent-Length: ${propfindXml.size}\r\n\r\n".toByteArray())
                                out.write(propfindXml)
                            }
                            "MKCOL", "MOVE" -> {
                                out.write("HTTP/1.1 201 Created\r\nContent-Length: 0\r\n\r\n".toByteArray())
                            }
                        }
                        out.flush()
                    }
                }
            }

            val session = WebDavSession(
                host = "127.0.0.1",
                port = port,
                https = false,
                basePath = "dav",
                username = "aiyu",
                password = "secret"
            )
            val items = session.list(1L, "")
            assertEquals(1, items.size)
            assertEquals("hello.txt", items[0].name)
            assertEquals(12L, items[0].size)

            session.mkdir("new_folder")
            session.rename("hello.txt", "renamed.txt")
            serverThread.join(5_000)
        }

        assertEquals(listOf("PROPFIND", "MKCOL", "MOVE"), seenMethods)
    }

    @Test
    fun `SmbRandomAccessFile caches 256KB window and reconnects on failure`() {
        val data = ByteArray(600 * 1024) { (it % 251).toByte() }
        var failNext = false
        var reopenCount = 0

        fun makeHandle(): RandomAccessHandle = object : RandomAccessHandle {
            override val size: Long = data.size.toLong()
            override fun readChunk(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
                if (failNext) {
                    failNext = false
                    throw IOException("Simulated connection drop")
                }
                val avail = minOf(length, data.size - position.toInt())
                if (avail <= 0) return -1
                System.arraycopy(data, position.toInt(), buffer, offset, avail)
                return avail
            }
            override fun close() {}
        }

        val raf = SmbRandomAccessFile(
            size = data.size.toLong(),
            initial = makeHandle(),
            reopen = {
                reopenCount++
                makeHandle()
            }
        )

        raf.use {
            val buf = ByteArray(1024)
            val n1 = it.readAt(100, buf, 0, 1024)
            assertEquals(1024, n1)
            assertArrayEquals(data.copyOfRange(100, 1124), buf)

            // Simulate connection drop and read outside current 256 KB window -> triggers reopen
            failNext = true
            val n2 = it.readAt(300 * 1024L, buf, 0, 1024)
            assertEquals(1024, n2)
            assertEquals(1, reopenCount)
            assertArrayEquals(data.copyOfRange(300 * 1024, 300 * 1024 + 1024), buf)
        }
    }
}
