package dev.liox.ambermusic.data.scanner

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.Locale

/** Only on Dispatchers.IO. Never loads embedded album bitmaps during a library scan. */
class AudioMetadataExtractor(private val context: Context) {

    data class Details(
        val title: String?,
        val artist: String?,
        val album: String?,
        val durationMs: Long?,
        val format: String,
        val bitrateBps: Long?,
        val bitrateSource: String,
        val sampleRateHz: Int?,
        val bitDepth: Int?,
        val genre: String?,
        val trackNumber: Int?,
        val discNumber: Int?
    )

    fun read(
        uri: Uri,
        fileName: String,
        mimeType: String?,
        bytes: Long,
        catalogDurationMs: Long
    ): Details {
        val format = formatFor(fileName, mimeType)
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var duration: Long? = null
        var bitrate: Long? = null
        var sampleRate: Int? = null
        var depth: Int? = null
        var genre: String? = null
        var trackNumber: Int? = null
        var discNumber: Int? = null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it in 1..999 }
            discNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                ?.substringBefore('/')?.trim()?.toIntOrNull()?.takeIf { it in 1..99 }
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.takeIf { it > 0L }
            bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toLongOrNull()?.takeIf { it > 0L }
            sampleRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                ?.toIntOrNull()?.takeIf { it > 0 }
            depth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                ?.toIntOrNull()?.takeIf { it in 1..64 }
        } catch (_: Exception) {
            // MediaStore metadata remains usable when a decoder doesn't recognize a file.
        } finally {
            retriever.release()
        }

        val header = when (format) {
            "FLAC" -> readFlacStreamInfo(context.contentResolver, uri)
            "WAV" -> readWavFmt(context.contentResolver, uri)
            else -> null
        }
        val durationMs = duration ?: catalogDurationMs.takeIf { it > 0L }
        val knownBitrate = bitrate
        val approximateBitrate = if (knownBitrate == null && bytes > 0L && durationMs != null) {
            ((bytes.toDouble() * 8_000.0) / durationMs.toDouble())
                .toLong().takeIf { it > 0L }
        } else null

        return Details(
            title = title?.takeIf { it.isNotBlank() },
            artist = artist?.takeIf { it.isNotBlank() },
            album = album?.takeIf { it.isNotBlank() },
            durationMs = durationMs,
            format = format,
            bitrateBps = knownBitrate ?: approximateBitrate,
            bitrateSource = when {
                knownBitrate != null -> "RETRIEVER"
                approximateBitrate != null -> "ESTIMATED"
                else -> "UNKNOWN"
            },
            sampleRateHz = header?.sampleRateHz ?: sampleRate,
            bitDepth = if (format == "FLAC" || format == "WAV") {
                header?.bitDepth ?: depth
            } else null,
            genre = genre?.take(80),
            trackNumber = trackNumber,
            discNumber = discNumber
        )
    }

    private data class Header(val sampleRateHz: Int, val bitDepth: Int)

    private fun formatFor(fileName: String, mime: String?): String {
        return when (fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "mp3" -> "MP3"
            "flac" -> "FLAC"
            "m4a", "mp4", "aac" -> "M4A"
            "wav", "wave" -> "WAV"
            "ogg", "oga", "opus" -> "OGG"
            else -> when (mime?.lowercase(Locale.ROOT)) {
                "audio/mpeg", "audio/mp3" -> "MP3"
                "audio/flac", "audio/x-flac" -> "FLAC"
                "audio/mp4", "audio/x-m4a", "audio/aac" -> "M4A"
                "audio/wav", "audio/x-wav", "audio/wave" -> "WAV"
                "audio/ogg", "audio/opus" -> "OGG"
                else -> "UNKNOWN"
            }
        }
    }

    private fun readFlacStreamInfo(resolver: ContentResolver, uri: Uri): Header? =
        try {
            resolver.openInputStream(uri)?.use { stream ->
                val input = BufferedInputStream(stream, 4096)
                if (!readBytes(input, 4).contentEquals(byteArrayOf(102, 76, 97, 67))) return@use null
                repeat(16) {
                    val block = readBytes(input, 4)
                    if (block.size != 4) return@use null
                    val blockType = block[0].toInt() and 0x7F
                    val size = ((block[1].toInt() and 255) shl 16) or
                        ((block[2].toInt() and 255) shl 8) or (block[3].toInt() and 255)
                    if (blockType == 0) {
                        if (size < 18 || size > 65_536) return@use null
                        val data = readBytes(input, size)
                        if (data.size != size) return@use null
                        val rate = ((data[10].toInt() and 255) shl 12) or
                            ((data[11].toInt() and 255) shl 4) or
                            ((data[12].toInt() and 240) ushr 4)
                        val depth = (((data[12].toInt() and 1) shl 4) or
                            ((data[13].toInt() and 240) ushr 4)) + 1
                        return@use if (rate > 0 && depth in 1..64) Header(rate, depth) else null
                    }
                    if (size > 2_097_152 || !skipExactly(input, size)) return@use null
                }
                null
            }
        } catch (_: Exception) {
            null
        }

    private fun readWavFmt(resolver: ContentResolver, uri: Uri): Header? =
        try {
            resolver.openInputStream(uri)?.use { stream ->
                val input = BufferedInputStream(stream, 4096)
                val riff = readBytes(input, 12)
                if (riff.size != 12 ||
                    String(riff, 0, 4, Charsets.US_ASCII) != "RIFF" ||
                    String(riff, 8, 4, Charsets.US_ASCII) != "WAVE"
                ) return@use null
                var readTotal = 12L
                while (readTotal < 1_048_576L) {
                    val chunk = readBytes(input, 8)
                    if (chunk.size != 8) break
                    readTotal += 8L
                    val size = (chunk[4].toLong() and 255) or
                        ((chunk[5].toLong() and 255) shl 8) or
                        ((chunk[6].toLong() and 255) shl 16) or
                        ((chunk[7].toLong() and 255) shl 24)
                    if (size > 1_048_576L || size < 0L) break
                    if (String(chunk, 0, 4, Charsets.US_ASCII) == "fmt ") {
                        if (size < 16L) return@use null
                        val data = readBytes(input, 16)
                        if (data.size != 16) return@use null
                        val sampleRate = (data[4].toInt() and 255) or
                            ((data[5].toInt() and 255) shl 8) or
                            ((data[6].toInt() and 255) shl 16) or
                            ((data[7].toInt() and 255) shl 24)
                        val depth = (data[14].toInt() and 255) or ((data[15].toInt() and 255) shl 8)
                        return@use if (sampleRate > 0 && depth in 1..64) Header(sampleRate, depth) else null
                    }
                    val padded = size + size % 2L
                    if (!skipExactly(input, padded.toInt())) break
                    readTotal += padded
                }
                null
            }
        } catch (_: Exception) {
            null
        }

    private fun readBytes(input: InputStream, amount: Int): ByteArray {
        val output = ByteArray(amount)
        var offset = 0
        while (offset < amount) {
            val n = input.read(output, offset, amount - offset)
            if (n < 0) break
            if (n == 0) continue
            offset += n
        }
        return if (offset == amount) output else output.copyOf(offset)
    }

    private fun skipExactly(input: InputStream, amount: Int): Boolean {
        var remaining = amount.toLong()
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) remaining -= skipped
            else if (input.read() == -1) return false
            else remaining--
        }
        return true
    }
}
