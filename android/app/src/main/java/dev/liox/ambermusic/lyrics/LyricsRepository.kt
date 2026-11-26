package dev.liox.ambermusic.lyrics

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** User imports an LRC/text file using the system document picker. No storage permission needed. */
class LyricsRepository(private val context: Context) {
    private val directory by lazy { File(context.filesDir, "lyrics").apply { mkdirs() } }
    private val autoSearch = context.getSharedPreferences("amber_lyrics_auto", Context.MODE_PRIVATE)
    private val searchMutex = Mutex()

    /**
     * One lookup for a newly played uncached song. Failed searches are cached for a day,
     * connectivity/server failures for 15 minutes. User-triggered searches bypass the cache.
     */
    suspend fun autoFetchFromLrclib(
        songId: Long, title: String, artist: String, album: String,
        durationMs: Long, modifiedAtSeconds: Long
    ): Boolean = withContext(Dispatchers.IO) {
        if (File(directory, "$songId.lrc").isFile) return@withContext false
        if (title.isBlank() || artist.isBlank()) return@withContext false
        val key = "track_${songId}_${modifiedAtSeconds}"
        val now = System.currentTimeMillis()
        if (now < autoSearch.getLong(key, 0L)) return@withContext false
        searchMutex.withLock {
            currentCoroutineContext().ensureActive()
            if (File(directory, "$songId.lrc").isFile) return@withLock false
            if (System.currentTimeMillis() < autoSearch.getLong(key, 0L)) return@withLock false
            try {
                val found = fetchFromLrclib(songId, title, artist, album, durationMs)
                if (!found) autoSearch.edit().putLong(key, System.currentTimeMillis() + 24L * 60L * 60L * 1000L).apply()
                found
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                autoSearch.edit().putLong(key, System.currentTimeMillis() + 15L * 60L * 1000L).apply()
                false
            }
        }
    }

    suspend fun import(songId: Long, source: Uri): Int = withContext(Dispatchers.IO) {
        val input = requireNotNull(context.contentResolver.openInputStream(source))
        val bytes = input.use { stream ->
            val buffer = ByteArray(8192)
            val output = ByteArrayOutputStream()
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                require(output.size() <= 512 * 1024) { "Archivo demasiado grande" }
            }
            output.toByteArray()
        }
        require(bytes.size <= 512 * 1024) { "El archivo de letras es demasiado grande" }
        val text = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
        val lines = parseLrc(text)
        require(lines.isNotEmpty()) { "No se encontraron letras" }
        val dest = File(directory, "$songId.lrc")
        val temp = File(directory, "$songId.part")
        try {
            temp.writeText(text, Charsets.UTF_8)
            check(temp.renameTo(dest) || run { temp.copyTo(dest, overwrite = true); true })
        } finally { temp.delete() }
        lines.size
    }

    suspend fun load(songId: Long): List<LyricLine> = withContext(Dispatchers.IO) {
        val file = File(directory, "$songId.lrc")
        if (!file.isFile || file.length() > 512 * 1024) emptyList()
        else parseLrc(file.readText(Charsets.UTF_8))
    }

    /** One-song lookup, used automatically on playback or explicitly from the Lyrics menu. */
    suspend fun fetchFromLrclib(
        songId: Long, title: String, artist: String, album: String, durationMs: Long
    ): Boolean = withContext(Dispatchers.IO) {
        val target = File(directory, "$songId.lrc")
        if (target.isFile) return@withContext false
        require(title.isNotBlank() && artist.isNotBlank()) { "Falta título o artista para buscar letras" }
        val builder = Uri.parse("https://lrclib.net/api/get").buildUpon()
            .appendQueryParameter("track_name", title.take(160))
            .appendQueryParameter("artist_name", artist.take(160))
        if (album.isNotBlank()) builder.appendQueryParameter("album_name", album.take(160))
        if (durationMs in 1000L..3_600_000L) builder.appendQueryParameter("duration", (durationMs / 1000L).toString())
        val connection = (URL(builder.build().toString()).openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 4500
            readTimeout = 6000
            setRequestProperty("User-Agent", "AmberMusic/2.3.0 (https://github.com/Liox269/ambermusic)")
            setRequestProperty("Accept", "application/json")
        }
        try {
            when (val status = connection.responseCode) {
                404 -> return@withContext false
                429 -> throw IllegalStateException("LRCLIB limitó las consultas. Inténtalo más tarde.")
                200 -> Unit
                else -> throw IllegalStateException("LRCLIB respondió HTTP $status")
            }
            val output = ByteArrayOutputStream()
            connection.inputStream.use { stream ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    require(output.size() <= 512 * 1024) { "Respuesta de LRCLIB demasiado grande" }
                }
            }
            currentCoroutineContext().ensureActive()
            val data = JSONObject(output.toString(Charsets.UTF_8.name()))
            if (data.optBoolean("instrumental", false)) return@withContext false
            val lrc = data.optString("syncedLyrics").takeUnless { it.isBlank() || it == "null" }
                ?: data.optString("plainLyrics").takeUnless { it.isBlank() || it == "null" }
                ?: return@withContext false
            if (parseLrc(lrc).isEmpty()) return@withContext false
            if (target.isFile) return@withContext false // Keep manual/imported lyrics.
            currentCoroutineContext().ensureActive()
            val tmp = File(directory, "$songId.lrclib.part")
            try {
                tmp.writeText(lrc, Charsets.UTF_8)
                currentCoroutineContext().ensureActive()
                check(tmp.renameTo(target) || run { tmp.copyTo(target, overwrite = true); true })
            } finally { tmp.delete() }
            true
        } finally { connection.disconnect() }
    }

    private val offsets = context.getSharedPreferences("amber_lrc_offsets", Context.MODE_PRIVATE)

    fun getOffsetMs(songId: Long): Long = offsets.getLong("offset_$songId", 0L)

    fun adjustOffset(songId: Long, byMs: Long): Long {
        val adjusted = (getOffsetMs(songId) + byMs).coerceIn(-30_000L, 30_000L)
        offsets.edit().putLong("offset_$songId", adjusted).apply()
        return adjusted
    }

    suspend fun loadAdjusted(songId: Long): List<LyricLine> =
        load(songId).map { if (it.timeMs < 0) it else it.copy(timeMs = (it.timeMs + getOffsetMs(songId)).coerceAtLeast(0)) }

    suspend fun saveEdited(songId: Long, text: String) = withContext(Dispatchers.IO) {
        require(text.toByteArray(Charsets.UTF_8).size < 512 * 1024) { "Letras demasiado largas" }
        require(parseLrc(text).isNotEmpty()) { "El contenido no contiene letras" }
        val file = File(directory, "$songId.lrc")
        val tmp = File(directory, "$songId.edit")
        try {
            tmp.writeText(text, Charsets.UTF_8)
            if (!tmp.renameTo(file)) { tmp.copyTo(file, true); tmp.delete() }
        } finally { tmp.delete() }
    }

    suspend fun clear(songId: Long) = withContext(Dispatchers.IO) {
        File(directory, "$songId.lrc").delete()
    }
}

data class LyricLine(val timeMs: Long, val text: String)

fun parseLrc(content: String): List<LyricLine> {
    val timestamps = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")
    val output = ArrayList<LyricLine>()
    val plain = ArrayList<String>()
    content.lineSequence().forEach { original ->
        val matches = timestamps.findAll(original).toList()
        val words = timestamps.replace(original, "").trim()
        if (matches.isEmpty()) {
            if (words.isNotEmpty() && !words.matches(Regex("\\[[a-zA-Z]+:.*]"))) plain.add(words)
        } else if (words.isNotEmpty()) {
            matches.forEach { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: 0L
                val seconds = match.groupValues[2].toLongOrNull() ?: 0L
                val fractional = match.groupValues[3]
                val fractionMs = when (fractional.length) {
                    1 -> (fractional.toLongOrNull() ?: 0L) * 100L
                    2 -> (fractional.toLongOrNull() ?: 0L) * 10L
                    3 -> fractional.toLongOrNull() ?: 0L
                    else -> 0L
                }
                output.add(LyricLine((minutes * 60L + seconds) * 1000L + fractionMs, words))
            }
        }
    }
    return if (output.isNotEmpty()) output.distinct().sortedBy { it.timeMs }
    else plain.map { LyricLine(-1L, it) }
}
