package dev.liox.ambermusic.domain.usecase

import dev.liox.ambermusic.domain.repository.MusicTrack
import java.util.Locale

/** Labels describe the LOCAL FILE, never the Bluetooth transport or DAC output. */
object AudioQualityLabel {
    fun detectedCodec(format: String, mime: String?): String {
        val type = mime?.lowercase(Locale.ROOT).orEmpty()
        return when {
            type.contains("opus") -> "OPUS"
            type.contains("vorbis") -> "VORBIS"
            type.contains("alac") -> "ALAC"
            type.contains("audio/3gpp") -> "AMR"
            type.contains("flac") -> "FLAC"
            type.contains("mp4a") || type.contains("aac") -> "AAC"
            type.contains("mpeg") -> "MP3"
            type.contains("raw") -> if (format.equals("WAV", ignoreCase = true)) "WAV PCM" else "PCM"
            else -> format.uppercase(Locale.ROOT)
        }
    }

    fun forTrack(track: MusicTrack): String {
        val codec = detectedCodec(track.format, track.codecMime)
        val khz = track.sampleRateHz?.takeIf { it in 8000..768000 }?.let { rate ->
            val value = if (rate % 1000 == 0) "${rate / 1000}"
                else String.format(Locale.US, "%.1f", rate / 1000.0)
            "${value}KHZ"
        }
        val isPcmLossless = codec in setOf("FLAC", "ALAC", "PCM", "WAV PCM") ||
            (track.format.equals("WAV", ignoreCase = true) && track.bitDepth != null)
        val resolution = if (isPcmLossless) {
            track.bitDepth?.takeIf { it in 8..64 }?.let { "${it}-BIT" }
        } else {
            track.bitrateBps?.takeIf { it > 0L }?.let { bps ->
                val approximate = if (track.bitrateSource == "ESTIMATED") "~" else ""
                "${approximate}${(bps + 500L) / 1000L}KBPS"
            }
        }
        val hiRes = isPcmLossless &&
            (track.bitDepth ?: 0) >= 24 && (track.sampleRateHz ?: 0) >= 88_200
        return listOfNotNull(if (hiRes) "HI-RES" else null, resolution, khz, codec)
            .joinToString(" ")
    }

    fun bitrateDescription(bps: Long?, source: String): String {
        val value = bps?.takeIf { it > 0 }?.let { "${(it + 500L) / 1000L} kbps" }
            ?: return "No disponible"
        return if (source == "ESTIMATED") "$value (estimación por tamaño/duración)" else value
    }
}
