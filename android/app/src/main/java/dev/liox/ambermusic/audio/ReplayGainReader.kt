package dev.liox.ambermusic.audio

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

/** Reads ReplayGain tags; never estimates gain from bitrate or normalizes blindly. */
class ReplayGainReader(private val context: Context) {
    suspend fun volumeFactor(uri: Uri, fileName: String, mode: String): Float = withContext(Dispatchers.IO) {
        if (mode == "off") return@withContext 1f
        val tags = try {
            context.contentResolver.openInputStream(uri)?.buffered()?.use { stream ->
                when (fileName.substringAfterLast('.', "").lowercase()) {
                    "flac" -> readFlac(stream)
                    "mp3" -> readId3(stream)
                    else -> emptyMap()
                }
            } ?: emptyMap()
        } catch (_: Exception) { emptyMap() }
        val prefix = if (mode == "album") "replaygain_album" else "replaygain_track"
        val gain = parseDb(tags["${prefix}_gain"] ?: tags["replaygain_track_gain"]) ?: return@withContext 1f
        val peak = tags["${prefix}_peak"]?.toFloatOrNull()?.takeIf { it > 0f }
        val linear = 10.0.pow(gain.coerceIn(-18.0, 12.0) / 20.0).toFloat()
        (if (peak != null) minOf(linear, 0.98f / peak) else minOf(1f, linear)).coerceIn(0.1f, 1f)
    }

    private fun parseDb(input: String?): Double? = input?.let {
        Regex("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)").find(it)?.value?.toDoubleOrNull()
    }

    private fun readFlac(stream: InputStream): Map<String, String> {
        if (!stream.readNBytesCompat(4).contentEquals("fLaC".toByteArray())) return emptyMap()
        repeat(32) {
            val b = stream.read()
            if (b == -1) return emptyMap()
            val lengthBytes = stream.readNBytesCompat(3)
            if (lengthBytes.size != 3) return emptyMap()
            val len = ((lengthBytes[0].toInt() and 255) shl 16) or
                ((lengthBytes[1].toInt() and 255) shl 8) or (lengthBytes[2].toInt() and 255)
            val type = b and 127
            if (type == 4) {
                if (len > 512_000) return emptyMap()
                return readVorbisComments(stream.readNBytesCompat(len))
            }
            if (len > 16_000_000 || !stream.skipFully(len.toLong())) return emptyMap()
            if (b and 128 != 0) return emptyMap()
        }
        return emptyMap()
    }

    private fun readVorbisComments(bytes: ByteArray): Map<String, String> {
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (data.remaining() < 8) return emptyMap()
        val vendorSize = data.int
        if (vendorSize < 0 || vendorSize > data.remaining() - 4) return emptyMap()
        data.position(data.position() + vendorSize)
        val count = data.int
        if (count !in 0..5000) return emptyMap()
        val tags = mutableMapOf<String, String>()
        repeat(count) {
            if (data.remaining() < 4) return tags
            val size = data.int
            if (size < 0 || size > data.remaining()) return tags
            val dataBytes = ByteArray(size)
            data.get(dataBytes)
            val line = dataBytes.toString(Charsets.UTF_8)
            val split = line.indexOf('=')
            if (split > 0) tags[line.substring(0, split).lowercase()] = line.substring(split + 1)
        }
        return tags
    }

    private fun readId3(stream: InputStream): Map<String, String> {
        val head = stream.readNBytesCompat(10)
        if (head.size < 10 || !head.copyOfRange(0,3).contentEquals("ID3".toByteArray())) return emptyMap()
        val ver = head[3].toInt() and 255
        if (ver !in 3..4) return emptyMap()
        val size = head.sliceArray(6..9).fold(0) { value, b -> (value shl 7) or (b.toInt() and 127) }
        if (size !in 1..512_000) return emptyMap()
        val all = stream.readNBytesCompat(size)
        val tags = mutableMapOf<String, String>()
        var cursor = 0
        while (cursor + 10 <= all.size) {
            val type = all.copyOfRange(cursor, cursor + 4).toString(Charsets.US_ASCII)
            if (!type.all { it.isLetterOrDigit() }) break
            val sz = if (ver == 4) (cursor + 4 until cursor + 8).fold(0) { a, i -> (a shl 7) or (all[i].toInt() and 127) }
                else (cursor + 4 until cursor + 8).fold(0) { a, i -> (a shl 8) or (all[i].toInt() and 255) }
            val start = cursor + 10
            if (sz <= 0 || start + sz > all.size) break
            if (type == "TXXX") {
                val content = all.copyOfRange(start, start + sz)
                if (content.size > 3 && (content[0] == 0.toByte() || content[0] == 3.toByte())) {
                    val payload = content.copyOfRange(1, content.size)
                    val delimiter = payload.indexOf(0)
                    if (delimiter > 0) {
                        val key = payload.copyOfRange(0, delimiter).toString(Charsets.UTF_8).lowercase()
                        val value = payload.copyOfRange(delimiter + 1, payload.size).toString(Charsets.UTF_8)
                        tags[key] = value
                    }
                }
            }
            cursor = start + sz
        }
        return tags
    }
}

private fun InputStream.readNBytesCompat(length: Int): ByteArray {
    val bytes = ByteArray(length)
    var got = 0
    while (got < length) {
        val n = read(bytes, got, length - got)
        if (n <= 0) break
        got += n
    }
    return if (got == length) bytes else bytes.copyOf(got)
}

private fun InputStream.skipFully(count: Long): Boolean {
    var remaining = count
    val buf = ByteArray(8192)
    while (remaining > 0) {
        val next = read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
        if (next <= 0) return false
        remaining -= next
    }
    return true
}
