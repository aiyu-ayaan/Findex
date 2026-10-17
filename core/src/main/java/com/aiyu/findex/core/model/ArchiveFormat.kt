package com.aiyu.findex.core.model

/**
 * Archive types Findex can extract. [writable] ones are offered when compressing;
 * only ZIP can be written with a password (Commons Compress can't encrypt 7z).
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
            ".tar" to TAR, ".zip" to ZIP, ".7z" to SEVEN_Z, ".rar" to RAR
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
