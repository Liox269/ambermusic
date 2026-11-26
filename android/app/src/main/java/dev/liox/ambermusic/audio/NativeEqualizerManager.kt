package dev.liox.ambermusic.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import kotlin.math.abs
import kotlin.math.ln

/** Six user-facing controls are mapped to the bands actually exposed by Android AudioEffect. */
object EqualizerProfiles {
    val frequencies = intArrayOf(60, 230, 910, 3600, 9000, 14000)

    fun key(routeName: String, bluetooth: Boolean): String = when {
        bluetooth -> "bluetooth"
        listOf("usb", "cable", "auricular", "wired", "headphone", "dac").any {
            routeName.contains(it, ignoreCase = true)
        } -> "wired"
        else -> "speaker"
    }

    fun nearestControl(centreHz: Int): Int {
        if (centreHz <= 0) return 0
        return frequencies.indices.minByOrNull {
            abs(ln(centreHz.toDouble()) - ln(frequencies[it].toDouble()))
        } ?: 0
    }
}

/** Owned by the playback service so EQ stays active when the UI is closed. */
class NativeEqualizerManager(context: Context) : AutoCloseable {
    private val prefs = context.applicationContext.getSharedPreferences("amber_native_equalizer", Context.MODE_PRIVATE)
    private var effect: Equalizer? = null
    private var audioSessionId = -1
    private var route = "speaker"
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "enabled" || key?.startsWith("gain_") == true) applyProfile()
    }

    init { prefs.registerOnSharedPreferenceChangeListener(listener) }

    fun updateSession(id: Int) {
        if (id == audioSessionId) return
        effect?.release()
        effect = null
        audioSessionId = id
        if (id > 0) {
            effect = runCatching { Equalizer(0, id) }.getOrNull()
            applyProfile()
        }
    }

    fun updateRoute(name: String, bluetooth: Boolean) {
        val next = EqualizerProfiles.key(name, bluetooth)
        if (route != next) {
            route = next
            applyProfile()
        }
    }

    private fun applyProfile() {
        val eq = effect ?: return
        runCatching {
            val enabled = prefs.getBoolean("enabled", false)
            if (!enabled) {
                eq.enabled = false
                return
            }
            val range = eq.bandLevelRange
            val min = range[0].toInt()
            val max = range[1].toInt()
            for (band in 0 until eq.numberOfBands.toInt()) {
                val centre = eq.getCenterFreq(band.toShort()).toInt() / 1000
                val control = EqualizerProfiles.nearestControl(centre)
                val dB = prefs.getFloat("gain_${route}_$control", 0f)
                eq.setBandLevel(band.toShort(), (dB * 100).toInt().coerceIn(min, max).toShort())
            }
            eq.enabled = true
        }
    }

    override fun close() {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
        effect?.release()
        effect = null
    }
}
