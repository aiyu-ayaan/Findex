package com.aiyu.findex.core.model

/**
 * Archive types Findex can extract. [writable] ones are offered when compressing;
 * only ZIP can be written with a password (Commons Compress can't encrypt 7z). RAR covers v4 and v5.
 */
enum class ArchiveFormat(val extension: String, val writable: Boolean, val passwordOnWrite: Boolean, val passwordOnRead: Boolean) {
    ZIP("zip", writable = true, passwordOnWrite = true, passwordOnRead = true),
    SEVEN_Z("7z", writable = true, passwordOnWrite = false, passwordOnRead = true),
    TAR("tar", writable = true, passwordOnWrite = false, passwordOnRead = false),
    TAR_GZ("tar.gz", writable = true, passwordOnWrite = false, passwordOnRead = false),
    TAR_XZ("tar.xz", writable = false, passwordOnWrite = false, passwordOnRead = false),
    TAR_BZ2("tar.bz2", writable = false, passwordOnWrite = false, passwordOnRead = false),
    RAR("rar", writable = false, passwordOnWrite = false, passwordOnRead = true);

    companion object {
        val Writable = entries.filter { it.writable }

        // Longest suffixes first, so "x.tar.gz" is TAR_GZ rather than an unknown ".gz".
        private val suffixes = listOf(
            ".tar.gz" to TAR_GZ, ".tgz" to TAR_GZ,
            ".tar.xz" to TAR_XZ, ".txz" to TAR_XZ,
            ".tar.bz2" to TAR_BZ2, ".tbz2" to TAR_BZ2,
            ".tar" to TAR, ".zip" to ZIP, ".zipx" to ZIP, ".7z" to SEVEN_Z, ".rar" to RAR
        )

        fun of(fileName: String): ArchiveFormat? {
            val lower = fileName.lowercase()
            return suffixes.firstOrNull { lower.endsWith(it.first) }?.second
        }

        /** `photos.tar.gz` -> `photos`. */
        fun baseName(fileName: String): String {
            val lower = fileName.lowercase()
            val suffix = suffixes.firstOrNull { lower.endsWith(it.first) }?.first ?: return fileName.substringBeforeLast('.')
            return fileName.dropLast(suffix.length)
        }
    }
}

/** One entry inside an archive; [path] uses '/' and has no leading slash. */
data class ArchiveEntry(val path: String, val isDirectory: Boolean, val size: Long, val modified: Long)

/**
 * Archive contents are browsed through virtual paths: `<archive file>!` is the archive root and
 * `<archive file>!/dir/file` an entry inside it, like `jar:` URLs.
 */
object ArchivePath {
    const val MARKER = "!"

    fun root(archive: String) = archive + MARKER

    /** Splits a virtual path into (archive file, inner path), or null for a normal file system path. */
    fun parse(path: String): Pair<String, String>? {
        var index = path.indexOf(MARKER)
        while (index >= 0) {
            val archive = path.substring(0, index)
            val rest = path.substring(index + MARKER.length)
            if ((rest.isEmpty() || rest.startsWith("/")) && ArchiveFormat.of(archive) != null) return archive to rest.trim('/')
            index = path.indexOf(MARKER, index + 1)
        }
        return null
    }

    fun of(archive: String, inner: String) = if (inner.isEmpty()) root(archive) else "${root(archive)}/$inner"

    /** Parent of a path that may be inside an archive; the archive root's parent is the archive's folder. */
    fun parent(path: String): String? {
        val (archive, inner) = parse(path) ?: return java.io.File(path).parent
        if (inner.isEmpty()) return java.io.File(archive).parent
        return of(archive, inner.substringBeforeLast('/', ""))
    }
}
