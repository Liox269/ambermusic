package dev.liox.ambermusic.ui

import kotlin.math.abs
import kotlin.math.roundToLong

/** Interpretation of gestures confined to the album cover, not Android system edges. */
internal object PlayerGestureLogic {
    enum class ArtworkGesture { NONE, PREVIOUS, NEXT, LIBRARY }

    fun coverGesture(dxPx: Float, dyPx: Float, thresholdPx: Float): ArtworkGesture {
        if (!dxPx.isFinite() || !dyPx.isFinite() || thresholdPx <= 0f) return ArtworkGesture.NONE
        val horizontal = abs(dxPx)
        val vertical = abs(dyPx)
        return when {
            dyPx > thresholdPx && vertical > horizontal * 1.25f -> ArtworkGesture.LIBRARY
            horizontal > thresholdPx && horizontal > vertical * 1.25f ->
                if (dxPx > 0) ArtworkGesture.PREVIOUS else ArtworkGesture.NEXT
            else -> ArtworkGesture.NONE
        }
    }

    /** Bounds seek actions; tapping the ends cannot seek outside the media duration. */
    fun seekPosition(xPx: Float, widthPx: Float, durationMs: Long): Long {
        if (!xPx.isFinite() || !widthPx.isFinite() || widthPx <= 0f || durationMs <= 0L) return 0L
        return (xPx.div(widthPx).coerceIn(0f, 1f) * durationMs).roundToLong().coerceIn(0L, durationMs)
    }
}
