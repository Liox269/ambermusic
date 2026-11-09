package dev.liox.ambermusic.lyrics

/** Stable lookup from Media3's playback clock. No lyric timing is invented. */
object LyricSyncPolicy {
    // The visual highlight can precede the timestamp slightly; this does not seek audio.
    const val VISUAL_LEAD_MS = 650L

    fun activeIndex(lines: List<LyricLine>, positionMs: Long, leadMs: Long = 0L): Int {
        if (lines.isEmpty() || positionMs < 0L) return -1
        val effectivePosition = (positionMs + leadMs.coerceIn(0L, 2500L))
            .coerceAtLeast(positionMs)
        // LRC parser sorts timed lines. Plain text (-1) is never falsely highlighted.
        var low = 0
        var high = lines.lastIndex
        var active = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            val time = lines[middle].timeMs
            if (time < 0L || time <= effectivePosition) {
                if (time >= 0L) active = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return active
    }

    fun seekPosition(x: Float, width: Float, durationMs: Long): Long {
        if (width <= 0f || durationMs <= 0L) return 0L
        val fraction = (x / width).coerceIn(0f, 1f)
        return (fraction * durationMs).toLong().coerceIn(0L, durationMs)
    }
}
