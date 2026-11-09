package dev.liox.ambermusic.lyrics

/** Stable lookup from Media3's playback clock. No lyric timing is invented. */
object LyricSyncPolicy {
    fun activeIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty() || positionMs < 0L) return -1
        // LRC parser sorts timed lines. Plain text (-1) is never falsely highlighted.
        var low = 0
        var high = lines.lastIndex
        var active = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            val time = lines[middle].timeMs
            if (time < 0L || time <= positionMs) {
                if (time >= 0L) active = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return active
    }
}
