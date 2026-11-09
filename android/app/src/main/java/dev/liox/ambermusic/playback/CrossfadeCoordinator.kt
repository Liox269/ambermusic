package dev.liox.ambermusic.playback

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * True two-stream overlap for local tracks. Both players are confined to the
 * Media3 application looper (main). The auxiliary decoder exists only close
 * to an automatic transition; MPRIS/MediaSession remains on the main player.
 * Fallback is ordinary ExoPlayer sequencing on unsupported/unknown durations.
 */
internal class CrossfadeCoordinator(
    private val context: Context,
    private val primary: ExoPlayer,
    private val scope: CoroutineScope,
    private val audioAttributes: AudioAttributes,
    private val seconds: () -> Int,
    private val targetVolume: () -> Float
) {
    private var auxiliary: ExoPlayer? = null
    private var fromId: String? = null
    private var nextId: String? = null
    private var nextIndex = C.INDEX_UNSET
    private var started = false
    private var handoff = false
    private var tickJob: Job? = null
    private var handoffJob: Job? = null

    val isMixing: Boolean get() = started || handoff

    fun start() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            while (true) {
                tick()
                val durationMs = seconds().coerceIn(0, 10) * 1000L
                val duration = primary.duration
                val remaining = if (duration > 0) (duration - primary.currentPosition).coerceAtLeast(0) else Long.MAX_VALUE
                delay(if (durationMs > 0 && remaining <= durationMs + 2_500L) 65L else 1_100L)
            }
        }
    }

    fun onTrackChanged(id: String?) {
        if (handoff && id == nextId) return
        reset()
    }

    private fun tick() {
        // After seekTo(nextIndex, position) the primary timeline is already on
        // the new song. Do not mistake that for a manual skip during handoff.
        if (handoff) return
        val length = seconds().coerceIn(0, 10) * 1000L
        if (length == 0L || !primary.isPlaying || primary.repeatMode == Player.REPEAT_MODE_ONE ||
            primary.isPlayingAd || primary.currentMediaItem == null) {
            if (!handoff) reset()
            return
        }
        val duration = primary.duration
        if (duration == C.TIME_UNSET || duration <= length + 1_000 || duration <= 0L) {
            reset()
            return
        }
        val remaining = duration - primary.currentPosition
        if (remaining <= 0) return
        val newIndex = primary.nextMediaItemIndex
        if (newIndex == C.INDEX_UNSET || newIndex == primary.currentMediaItemIndex) {
            reset()
            return
        }
        val newItem = primary.getMediaItemAt(newIndex)
        if (auxiliary != null &&
            (nextId != newItem.mediaId || fromId != primary.currentMediaItem?.mediaId)) {
            reset()
        }
        if (auxiliary == null && remaining <= length + 1_200L) {
            // Focus is managed by the primary player. Do not request it twice.
            auxiliary = ExoPlayer.Builder(context)
                .setAudioAttributes(audioAttributes, false).build().also { secondary ->
                    secondary.volume = 0f
                    secondary.setMediaItem(newItem)
                    secondary.prepare()
                }
            fromId = primary.currentMediaItem?.mediaId
            nextId = newItem.mediaId
            nextIndex = newIndex
        }
        val secondary = auxiliary ?: return
        if (remaining <= length && secondary.playbackState == Player.STATE_READY && !started) {
            secondary.play()
            started = true
        }
        if (!started || handoff) return
        val ratio = ((length - remaining).toFloat() / length).coerceIn(0f, 1f)
        val (outGain, inGain) = CrossfadeMath.gains(ratio)
        val gain = targetVolume().coerceIn(0f, 1f)
        primary.volume = gain * outGain
        secondary.volume = gain * inGain
        if (remaining <= 160L) {
            handoff = true
            primary.volume = 0f
            val resumePosition = secondary.currentPosition.coerceAtLeast(0L)
            primary.seekTo(nextIndex, resumePosition)
            handoffJob = scope.launch {
                // Keep the auxiliary track audible during the primary seek/buffer.
                for (attempt in 0 until 30) {
                    if (primary.currentMediaItem?.mediaId == nextId &&
                        primary.playbackState == Player.STATE_READY) break
                    delay(40L)
                }
                if (primary.currentMediaItem?.mediaId == nextId) {
                    repeat(6) { step ->
                        val f = (step + 1) / 6f
                        primary.volume = gain * f
                        secondary.volume = gain * (1f - f)
                        delay(20L)
                    }
                }
                reset()
            }
        }
    }

    fun stop() {
        tickJob?.cancel()
        reset()
    }

    private fun reset() {
        val wasMixing = isMixing
        handoffJob?.cancel()
        handoffJob = null
        auxiliary?.release()
        auxiliary = null
        fromId = null
        nextId = null
        nextIndex = C.INDEX_UNSET
        started = false
        handoff = false
        if (wasMixing) primary.volume = targetVolume().coerceIn(0f, 1f)
    }
}
