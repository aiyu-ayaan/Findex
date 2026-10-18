package com.aiyu.findex.core.db

import org.junit.Assert.assertEquals
import org.junit.Test

class PathIndexTest {
    @Test
    fun likePrefixEscapesWildcards() {
        assertEquals("/sdcard/my\\_dir\\%x\\\\y/%", likePrefix("/sdcard/my_dir%x\\y/"))
        assertEquals("smb://3/a/%", likePrefix("smb://3/a"))
    }
}
