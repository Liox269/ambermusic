package dev.liox.ambermusic.visual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientBlurKernelTest {
    @Test fun uniformColorsRemainUniform() {
        val original = 0xFF8A23BC.toInt()
        val pixels = IntArray(23 * 17) { original }
        AmbientBlurKernel.blurInPlace(pixels, 23, 17, radius = 8)
        assertTrue(pixels.all { it == original })
    }

    @Test fun sharpEdgeBecomesSmoothAndOpaque() {
        val width = 71
        val height = 55
        val pixels = IntArray(width * height) { i ->
            if (i % width < width / 2) 0xFFFF0000.toInt() else 0xFF0000FF.toInt()
        }
        AmbientBlurKernel.blurInPlace(pixels, width, height, radius = 9)
        assertTrue(pixels.all { (it ushr 24) == 255 })
        val center = pixels[(height / 2) * width + (width / 2)]
        assertTrue(((center ushr 16) and 255) in 40..220)
        assertTrue((center and 255) in 40..220)
        for (x in 1 until width) {
            val before = (pixels[(height / 2) * width + x - 1] ushr 16) and 255
            val after = (pixels[(height / 2) * width + x] ushr 16) and 255
            assertTrue(kotlin.math.abs(after - before) <= 12)
        }
    }

    @Test fun cacheBlurPreservesImageSize() {
        val width = 448
        val height = 960
        val pixels = IntArray(width * height) { i ->
            (0xFF shl 24) or ((i % width * 255 / width) shl 16) or 0x7D32
        }
        AmbientBlurKernel.blurInPlace(pixels, width, height, radius = 33)
        assertEquals(width * height, pixels.size)
    }
}
