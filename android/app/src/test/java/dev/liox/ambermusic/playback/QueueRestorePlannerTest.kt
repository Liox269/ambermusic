package dev.liox.ambermusic.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class QueueRestorePlannerTest {
    @Test fun missingPreviousTrackDoesNotShiftCurrentSong() {
        assertEquals(1, QueueRestorePlanner.selectedIndex(
            savedIds = listOf(11L, 22L, 33L, 44L),
            survivingOriginalIndices = listOf(0, 2, 3),
            previousIndex = 2,
            previousSongId = 33L
        ))
    }
    @Test fun missingCurrentTrackSelectsNextWithZeroPositionInCaller() {
        assertEquals(1, QueueRestorePlanner.selectedIndex(
            listOf(11L, 22L, 33L), listOf(0, 2), 1, 22L
        ))
    }
    @Test fun removedLastTrackFallsBackToPrevious() {
        assertEquals(1, QueueRestorePlanner.selectedIndex(
            listOf(11L, 22L, 33L), listOf(0, 1), 2, 33L
        ))
    }
    @Test fun duplicateTrackIdsSelectNearestOccurrence() {
        assertEquals(2, QueueRestorePlanner.selectedIndex(
            listOf(11L, 22L, 11L, 44L), listOf(0, 1, 2, 3), 2, 11L
        ))
    }
    @Test fun noSurvivingSongsReturnsMinusOne() {
        assertEquals(-1, QueueRestorePlanner.selectedIndex(listOf(1L), emptyList(), 0, 1L))
    }
}
