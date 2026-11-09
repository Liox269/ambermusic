package dev.liox.ambermusic.lyrics

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricSyncPolicyTest {
    private val lines = listOf(
        LyricLine(0L, "Uno"),
        LyricLine(1800L, "Dos"),
        LyricLine(5200L, "Tres"),
        LyricLine(7900L, "Cuatro")
    )

    @Test fun anticipatesHighlightButNotAudio() {
        assertEquals(0, LyricSyncPolicy.activeIndex(lines, 1200L))
        assertEquals(1, LyricSyncPolicy.activeIndex(lines, 1200L, 650L))
        assertEquals(0, LyricSyncPolicy.activeIndex(lines, 1000L, 650L))
    }

    @Test fun seekbarPositionsAreClamped() {
        assertEquals(0L, LyricSyncPolicy.seekPosition(-20f, 100f, 60000L))
        assertEquals(30000L, LyricSyncPolicy.seekPosition(50f, 100f, 60000L))
        assertEquals(60000L, LyricSyncPolicy.seekPosition(200f, 100f, 60000L))
        assertEquals(0L, LyricSyncPolicy.seekPosition(4f, 0f, 60000L))
    }

    @Test fun startsAtFirstLine() {
        assertEquals(0, LyricSyncPolicy.activeIndex(lines, 0L))
        assertEquals(0, LyricSyncPolicy.activeIndex(lines, 1799L))
    }

    @Test fun movesAtTimestamps() {
        assertEquals(1, LyricSyncPolicy.activeIndex(lines, 1800L))
        assertEquals(2, LyricSyncPolicy.activeIndex(lines, 5200L))
        assertEquals(3, LyricSyncPolicy.activeIndex(lines, 30000L))
    }

    @Test fun backwardSeekSelectsEarlierLine() {
        assertEquals(1, LyricSyncPolicy.activeIndex(lines, 6400L).let { LyricSyncPolicy.activeIndex(lines, 2000L) })
    }

    @Test fun plainLyricsHaveNoFalseActiveLine() {
        assertEquals(-1, LyricSyncPolicy.activeIndex(listOf(LyricLine(-1L, "Sin tiempos")), 5000L))
        assertEquals(-1, LyricSyncPolicy.activeIndex(emptyList(), 1000L))
    }

    @Test fun handlesNegativePositions() {
        assertEquals(-1, LyricSyncPolicy.activeIndex(lines, -1L))
    }
}
