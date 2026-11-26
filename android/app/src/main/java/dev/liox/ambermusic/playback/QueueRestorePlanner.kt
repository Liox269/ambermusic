package dev.liox.ambermusic.playback

import kotlin.math.abs

/**
 * Restores a selected song by its stable MediaStore ID, not the shifted index after
 * unavailable or hidden songs have been excluded. No Android dependencies: unit-testable.
 */
object QueueRestorePlanner {
    fun selectedIndex(
        savedIds: List<Long>,
        survivingOriginalIndices: List<Int>,
        previousIndex: Int,
        previousSongId: Long?
    ): Int {
        if (survivingOriginalIndices.isEmpty()) return -1
        val anchor = previousIndex.coerceIn(0, (savedIds.size - 1).coerceAtLeast(0))
        val wantedId = previousSongId ?: savedIds.getOrNull(anchor)
        if (wantedId != null) {
            val exact = survivingOriginalIndices.withIndex()
                .filter { (_, original) -> savedIds.getOrNull(original) == wantedId }
                .minByOrNull { (_, original) -> abs(original - anchor) }
            if (exact != null) return exact.index
        }
        // If the selected file was removed, prefer the next playable item,
        // otherwise the previous one. Never use a shifted numeric index.
        val next = survivingOriginalIndices.indexOfFirst { it >= anchor }
        return if (next >= 0) next else survivingOriginalIndices.lastIndex
    }
}
