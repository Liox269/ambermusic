package dev.liox.ambermusic.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerGestureLogicTest {
    @Test fun coverGesturesAreDirectional() {
        assertEquals(PlayerGestureLogic.ArtworkGesture.LIBRARY, PlayerGestureLogic.coverGesture(0f, 140f, 60f))
        assertEquals(PlayerGestureLogic.ArtworkGesture.NEXT, PlayerGestureLogic.coverGesture(-90f, 10f, 60f))
        assertEquals(PlayerGestureLogic.ArtworkGesture.PREVIOUS, PlayerGestureLogic.coverGesture(95f, -10f, 60f))
        assertEquals(PlayerGestureLogic.ArtworkGesture.NONE, PlayerGestureLogic.coverGesture(20f, 20f, 60f))
        assertEquals(PlayerGestureLogic.ArtworkGesture.NONE, PlayerGestureLogic.coverGesture(95f, 95f, 60f))
    }
    @Test fun seekingNeverExceedsTrackBounds() {
        assertEquals(0L, PlayerGestureLogic.seekPosition(-40f, 100f, 300_000L))
        assertEquals(150_000L, PlayerGestureLogic.seekPosition(50f, 100f, 300_000L))
        assertEquals(300_000L, PlayerGestureLogic.seekPosition(140f, 100f, 300_000L))
        assertEquals(0L, PlayerGestureLogic.seekPosition(50f, 0f, 300_000L))
        assertEquals(0L, PlayerGestureLogic.seekPosition(40f, 100f, 0L))
    }
}
