package com.aiyu.findex.core.operation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageTransformerTest {
    private fun vp8xHeader(flags: Int): ByteArray {
        val b = ByteArray(24)
        "RIFF".toByteArray().copyInto(b, 0)
        "WEBP".toByteArray().copyInto(b, 8)
        "VP8X".toByteArray().copyInto(b, 12)
        b[16] = 10
        b[20] = flags.toByte()
        return b
    }

    @Test fun animatedWebpDetected() {
        assertTrue(ImageTransformer.isAnimatedWebp(vp8xHeader(0x02)))
        assertTrue(ImageTransformer.isAnimatedWebp(vp8xHeader(0x12))) // alpha + animation
    }

    @Test fun staticWebpAndOtherFormatsNotFlagged() {
        assertFalse(ImageTransformer.isAnimatedWebp(vp8xHeader(0x10))) // alpha only
        assertFalse(ImageTransformer.isAnimatedWebp("RIFF\u0000\u0000\u0000\u0000WEBPVP8 \u0000\u0000\u0000\u0000\u0002".toByteArray()))
        assertFalse(ImageTransformer.isAnimatedWebp(ByteArray(10)))
    }
}
