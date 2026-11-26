package dev.liox.ambermusic.lyrics

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

class LyricsRepository(private val context: Context) {
    private val directory by lazy { File(context.filesDir, "lyrics").apply { mkdirs() } }
    private val autoSearch = context.getSharedPreferences("amber_lyrics_auto", Context.MODE_PRIVATE)
    private val searchMutex = Mutex()
    private val offsets = context.getSharedPreferences("amber_lrc_offsets", Context.MODE_PRIVATE)

    fun hasLyrics(songId: Long): Boolean {
        val file = File(directory, "$songId.lrc")
        return file.isFile && file.length() in 1..(512 * 1024)
    }

    suspend fun autoFetchFromLrclib(
        songId: Long, title: String, artist: String, album: String,
        durationMs: Long, modifiedAtSeconds: Long
    ): Boolean = withContext(Dispatchers.IO) {
        if (hasLyrics(songId)) return@withContext false
        if (title.isBlank()) return@withContext false
        val key = "track_${songId}_${modifiedAtSeconds}"
        val now = System.currentTimeMillis()
        if (now < autoSearch.getLong(key, 0L)) return@withContext false
        searchMutex.withLock {
            currentCoroutineContext().ensureActive()
            if (hasLyrics(songId)) return@withLock false
            if (System.currentTimeMillis() < autoSearch.getLong(key, 0L)) return@withLock false
            try {
                val found = fetchFromLrclib(songId, title, artist, album, durationMs)
                if (!found) autoSearch.edit().putLong(key, System.currentTimeMillis() + 24L * 60L * 60L * 1000L).apply()
                found
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
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
        writeLyricsAtomically(songId, text)
        lines.size
    }

    suspend fun load(songId: Long): List<LyricLine> = withContext(Dispatchers.IO) {
        val file = File(directory, "$songId.lrc")
        if (!file.isFile || file.length() > 512 * 1024) emptyList()
        else parseLrc(file.readText(Charsets.UTF_8))
    }

    /** Multi-source lookup ported from AmberMusix PC: LRCLIB exact -> LRCLIB Search + NetEase -> Lyrics.ovh */
    suspend fun fetchFromLrclib(
        songId: Long, title: String, artist: String, album: String, durationMs: Long
    ): Boolean = withContext(Dispatchers.IO) {
        if (hasLyrics(songId)) return@withContext false
        require(title.isNotBlank()) { "Falta título para buscar letras" }

        val cleanArtist = cleanSearchTerm(artist).takeUnless {
            it.equals("unknown", ignoreCase = true) || it.equals("desconocido", ignoreCase = true)
        }.orEmpty()
        val cleanTitle = cleanSearchTerm(title).ifBlank { title.trim() }

        // 1. LRCLIB exact match
        if (artist.isNotBlank()) {
            val exact = runCatching { queryLrclibExact(title, artist, album, durationMs) }.getOrNull()
            if (!exact.isNullOrBlank() && saveCandidate(songId, exact)) return@withContext true
        }

        // 2. Parallel LRCLIB search + NetEase (title + artist, then title only), preferring synced LRC
        val queries = buildList {
            if (cleanArtist.isNotBlank()) add("$cleanTitle $cleanArtist")
            add(cleanTitle)
        }.distinct()

        for (query in queries) {
            currentCoroutineContext().ensureActive()
            val candidate = searchParallelSources(query)
            if (!candidate.isNullOrBlank() && saveCandidate(songId, candidate)) return@withContext true
        }

        // 3. Final fallback: Lyrics.ovh
        if (cleanArtist.isNotBlank()) {
            currentCoroutineContext().ensureActive()
            val ovh = runCatching { queryLyricsOvh(cleanArtist, cleanTitle) }.getOrNull()
            if (!ovh.isNullOrBlank() && saveCandidate(songId, ovh)) return@withContext true
        }

        false
    }

    private suspend fun searchParallelSources(query: String): String? = coroutineScope {
        val lrclib = async { runCatching { queryLrclibSearch(query) }.getOrNull() }
        val netease = async { runCatching { queryNetEase(query) }.getOrNull() }
        val results = listOfNotNull(lrclib.await(), netease.await())
        results.firstOrNull { isSyncedLrc(it) } ?: results.firstOrNull()
    }

    private fun isSyncedLrc(text: String): Boolean =
        text.contains("[") && text.contains(":") && text.contains("]") &&
            parseLrc(text).any { it.timeMs >= 0L }

    private fun queryLrclibExact(title: String, artist: String, album: String, durationMs: Long): String? {
        val builder = Uri.parse("https://lrclib.net/api/get").buildUpon()
            .appendQueryParameter("track_name", title.take(160))
            .appendQueryParameter("artist_name", artist.take(160))
        if (album.isNotBlank()) builder.appendQueryParameter("album_name", album.take(160))
        if (durationMs in 1000L..3_600_000L) builder.appendQueryParameter("duration", (durationMs / 1000L).toString())
        val raw = httpGet(builder.build().toString()) ?: return null
        val data = JSONObject(raw)
        if (data.optBoolean("instrumental", false)) return null
        return data.optString("syncedLyrics").takeUnless { it.isBlank() || it == "null" }
            ?: data.optString("plainLyrics").takeUnless { it.isBlank() || it == "null" }
    }

    private fun queryLrclibSearch(query: String): String? {
        val url = Uri.parse("https://lrclib.net/api/search").buildUpon()
            .appendQueryParameter("q", query.take(180))
            .build().toString()
        val raw = httpGet(url) ?: return null
        val arr = JSONArray(raw)
        var plainFallback: String? = null
        for (i in 0 until minOf(arr.length(), 8)) {
            val item = arr.optJSONObject(i) ?: continue
            if (item.optBoolean("instrumental", false)) continue
            val synced = item.optString("syncedLyrics").takeUnless { it.isBlank() || it == "null" }
            if (synced != null && isSyncedLrc(synced)) return synced
            if (plainFallback == null) {
                plainFallback = item.optString("plainLyrics").takeUnless { it.isBlank() || it == "null" }
            }
        }
        return plainFallback
    }

    private fun queryNetEase(query: String): String? {
        val searchUrl = Uri.parse("https://music.163.com/api/search/get").buildUpon()
            .appendQueryParameter("s", query.take(160))
            .appendQueryParameter("type", "1")
            .appendQueryParameter("limit", "5")
            .build().toString()
        val rawSearch = httpGet(searchUrl, referer = "https://music.163.com/") ?: return null
        val songs = JSONObject(rawSearch).optJSONObject("result")?.optJSONArray("songs") ?: return null
        var plainFallback: String? = null
        for (i in 0 until minOf(songs.length(), 4)) {
            val id = songs.optJSONObject(i)?.optLong("id", -1L) ?: -1L
            if (id <= 0L) continue
            val lyricUrl = "https://music.163.com/api/song/lyric?id=$id&lv=1"
            val rawLyric = httpGet(lyricUrl, referer = "https://music.163.com/") ?: continue
            val lrc = JSONObject(rawLyric).optJSONObject("lrc")?.optString("lyric")
                ?.takeUnless { it.isBlank() || it == "null" } ?: continue
            if (isSyncedLrc(lrc)) return lrc
            if (plainFallback == null) plainFallback = lrc
        }
        return plainFallback
    }

    private fun queryLyricsOvh(artist: String, title: String): String? {
        val a = URLEncoder.encode(artist.take(120), "UTF-8").replace("+", "%20")
        val t = URLEncoder.encode(title.take(120), "UTF-8").replace("+", "%20")
        val raw = httpGet("https://api.lyrics.ovh/v1/$a/$t", readTimeoutMs = 6000) ?: return null
        return JSONObject(raw).optString("lyrics").takeUnless { it.isBlank() || it == "null" }
    }

    private fun httpGet(url: String, referer: String? = null, readTimeoutMs: Int = 7000): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 4500
            readTimeout = readTimeoutMs
            setRequestProperty("User-Agent", "AmberMusic/2.9.0 (https://github.com/Liox269/ambermusic)")
            setRequestProperty("Accept", "application/json")
            if (referer != null) setRequestProperty("Referer", referer)
        }
        return try {
            if (conn.responseCode != 200) return null
            val out = ByteArrayOutputStream()
            conn.inputStream.use { stream ->
                val buf = ByteArray(8192)
                while (true) {
                    val read = stream.read(buf)
                    if (read < 0) break
                    out.write(buf, 0, read)
                    if (out.size() > 512 * 1024) break
                }
            }
            out.toString(Charsets.UTF_8.name())
        } finally {
            conn.disconnect()
        }
    }

    private fun saveCandidate(songId: Long, lrc: String): Boolean {
        if (parseLrc(lrc).isEmpty()) return false
        if (hasLyrics(songId)) return false
        writeLyricsAtomically(songId, lrc)
        return true
    }

    private fun writeLyricsAtomically(songId: Long, text: String) {
        val dest = File(directory, "$songId.lrc")
        val tmp = File(directory, "$songId.part")
        try {
            tmp.writeText(text, Charsets.UTF_8)
            check(tmp.renameTo(dest) || run { tmp.copyTo(dest, overwrite = true); true })
        } finally {
            tmp.delete()
        }
    }

    fun cleanSearchTerm(raw: String): String = raw
        .substringBefore("//")
        .replace(Regex("\([^)]*\)"), " ")
        .replace(Regex("\[[^\]]*\]"), " ")
        .replace(Regex("\s*-\s*Remastered.*$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\b(feat\.?|ft\.?)\b.*$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\s+"), " ")
        .trim()

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
        writeLyricsAtomically(songId, text)
    }

    suspend fun clear(songId: Long) = withContext(Dispatchers.IO) {
        File(directory, "$songId.lrc").delete()
    }
}

data class LyricLine(val timeMs: Long, val text: String)

fun parseLrc(content: String): List<LyricLine> {
    val timestamps = Regex("\\[(\\d{1,3}):(\\d{2})(?:[:.](\\d{1,3}))?\\]")
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
