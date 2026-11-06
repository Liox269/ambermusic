package dev.liox.ambermusic.domain.usecase

import dev.liox.ambermusic.domain.repository.MusicTrack
import java.util.Locale

object AudioQualityLabel {
    fun forTrack(track: MusicTrack): String {
        val format = track.format.uppercase(Locale.ROOT)
        val sample = track.sampleRateHz?.let { rate ->
            val khz = if (rate % 1000 == 0) "${rate / 1000}" else
                String.format(Locale.US, "%.1f", rate / 1000.0)
            "${khz}KHZ"
        }
        val quality = if (format in setOf("FLAC", "WAV") && track.bitDepth != null) {
            "${track.bitDepth}-BIT"
        } else {
            track.bitrateBps?.let { bits ->
                val rounded = (bits + 500) / 1000
                val prefix = if (track.bitrateSource == "ESTIMATED") "~" else ""
                "${prefix}${rounded}KBPS"
            }
        }
        return listOfNotNull(quality, sample, format).joinToString(" ")
    }
}
