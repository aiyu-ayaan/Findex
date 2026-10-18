package com.aiyu.findex.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleasesTest {
    private fun release(tag: String) = Release(Version.parse(tag)!!, tag, "", listOf(ReleaseAsset("Findex-$tag.apk", "u", 1)))

    @Test fun stableBeatsItsPreReleases() {
        assertTrue(Version.parse("1.0.0")!! > Version.parse("1.0.0-beta.9")!!)
        assertTrue(Version.parse("1.0.0-beta.1")!! > Version.parse("1.0.0-alpha.7")!!)
    }

    @Test fun unknownTagsAreSkipped() = assertNull(Version.parse("nightly"))

    @Test fun channelFiltersPreReleases() {
        val all = listOf(release("v1.1.0-alpha.1"), release("v1.0.1"))
        val installed = Version.parse("1.0.0")
        assertEquals("v1.0.1", Releases.pick(all, installed, UpdateChannel.STABLE)?.name)
        assertEquals("v1.1.0-alpha.1", Releases.pick(all, installed, UpdateChannel.ALPHA)?.name)
        assertNull(Releases.pick(all, Version.parse("2.0.0"), UpdateChannel.ALPHA))
    }
}
