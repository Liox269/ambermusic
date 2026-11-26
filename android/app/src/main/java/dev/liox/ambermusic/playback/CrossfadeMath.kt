package dev.liox.ambermusic.playback

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Predictable equal-power weights for an overlap; no Android dependencies. */
internal object CrossfadeMath {
    fun gains(progress: Float): Pair<Float, Float> {
        val angle = progress.coerceIn(0f, 1f) * PI / 2.0
        return cos(angle).toFloat() to sin(angle).toFloat()
    }
}
