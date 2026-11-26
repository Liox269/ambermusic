package dev.liox.ambermusic.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossfadeMathTest {
    @Test fun startsWithOnlyTheFirstStream() {
        val (a, b) = CrossfadeMath.gains(0f)
        assertEquals(1f, a, .0001f)
        assertEquals(0f, b, .0001f)
    }

    @Test fun midpointKeepsEqualPower() {
        val (a, b) = CrossfadeMath.gains(.5f)
        assertEquals(a, b, .0001f)
        assertEquals(1f, a * a + b * b, .001f)
    }

    @Test fun endsWithOnlyTheSecondStream() {
        val (a, b) = CrossfadeMath.gains(1f)
        assertEquals(0f, a, .0001f)
        assertEquals(1f, b, .0001f)
    }

    @Test fun clampsOutOfRangeProgress() {
        assertEquals(CrossfadeMath.gains(0f), CrossfadeMath.gains(-5f))
        assertEquals(CrossfadeMath.gains(1f), CrossfadeMath.gains(8f))
    }

    @Test fun curveIsMonotonic() {
        val values = (0..20).map { CrossfadeMath.gains(it / 20f) }
        assertTrue(values.zipWithNext().all { (a,b) -> a.first >= b.first && a.second <= b.second })
    }
}
