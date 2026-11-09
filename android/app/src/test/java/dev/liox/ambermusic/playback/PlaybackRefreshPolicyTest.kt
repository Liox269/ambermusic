package dev.liox.ambermusic.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRefreshPolicyTest {
    @Test fun noExcessiveSnapshots() {
        assertFalse(PlaybackRefreshPolicy.shouldPersist(100_000L, 109_999L))
        assertTrue(PlaybackRefreshPolicy.shouldPersist(100_000L, 110_000L))
    }

    @Test fun monotonicClockReset() {
        assertTrue(PlaybackRefreshPolicy.shouldPersist(100_000L, 30L))
    }

    @Test fun boundedPolling() {
        assertTrue(PlaybackRefreshPolicy.PROGRESS_POLL_MS >= 500L)
        assertTrue(PlaybackRefreshPolicy.PROGRESS_POLL_MS <= 1_000L)
    }
}