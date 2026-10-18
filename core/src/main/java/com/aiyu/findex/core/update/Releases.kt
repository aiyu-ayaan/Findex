package com.aiyu.findex.core.update

/** Which builds a device is willing to be offered. */
enum class UpdateChannel(val stage: Int) {
    STABLE(Version.STABLE),
    BETA(Version.BETA),
    ALPHA(Version.ALPHA);

    /** A channel takes its own builds and everything steadier. */
    fun accepts(version: Version): Boolean = version.stage >= stage

    companion object {
        /** An alpha install defaults to alphas, otherwise it would be stranded until its version is released. */
        fun forVersion(versionName: String): UpdateChannel = when (Version.parse(versionName)?.stage) {
            Version.ALPHA -> ALPHA
            Version.BETA -> BETA
            else -> STABLE
        }
    }
}

/** Only the shapes release.yml produces: `1.2.3`, `1.2.3-alpha.4`, `1.2.3-beta.4`, optional leading `v`. */
data class Version(val major: Int, val minor: Int, val patch: Int, val stage: Int, val stageNumber: Int) : Comparable<Version> {

    override fun compareTo(other: Version): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch }, { it.stage }, { it.stageNumber })

    companion object {
        const val ALPHA = 0
        const val BETA = 1
        const val STABLE = 2

        private val PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta)\.(\d+))?$""", RegexOption.IGNORE_CASE)

        fun parse(text: String?): Version? {
            val (major, minor, patch, label, number) = PATTERN.matchEntire(text?.trim().orEmpty())?.destructured ?: return null
            return Version(
                major.toInt(), minor.toInt(), patch.toInt(),
                stage = when (label.lowercase()) {
                    "alpha" -> ALPHA
                    "beta" -> BETA
                    else -> STABLE
                },
                // A stable release must beat `-beta.9` of the same numbers.
                stageNumber = number.toIntOrNull() ?: Int.MAX_VALUE
            )
        }
    }
}

data class ReleaseAsset(val name: String, val url: String, val size: Long)

data class Release(val version: Version, val name: String, val notes: String, val assets: List<ReleaseAsset>)

object Releases {
    const val REPOSITORY = "aiyu-ayaan/Findex"
    const val API = "https://api.github.com/repos/$REPOSITORY/releases?per_page=30"

    /** Newest release on [channel] that beats [installed], by version and never by publish date. */
    fun pick(releases: List<Release>, installed: Version?, channel: UpdateChannel): Release? =
        releases
            .filter { channel.accepts(it.version) && (installed == null || it.version > installed) }
            .maxByOrNull { it.version }

    /** CI publishes one universal `Findex-<version>.apk`. */
    fun apkFor(release: Release): ReleaseAsset? = release.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
}
