package dev.liox.ambermusic.playback

/**
 * Conserves CPU/wakeups while playing in background. Media3 publishes actual playback changes
 * through the Player.Listener, while progress-only polling can run less frequently.
 */
object PlaybackRefreshPolicy {
    const val PROGRESS_POLL_MS = 750L
    const val LYRICS_POLL_MS = 150L
    const val PERSIST_POSITION_MS = 10_000L
    fun shouldPersist(lastSnapshotMs: Long, nowMs: Long): Boolean =
        nowMs - lastSnapshotMs >= PERSIST_POSITION_MS || nowMs < lastSnapshotMs
}
