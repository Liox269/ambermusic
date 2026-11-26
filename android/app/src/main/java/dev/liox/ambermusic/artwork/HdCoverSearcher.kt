package dev.liox.ambermusic.artwork

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class HdCoverCandidate(
    val title: String,
    val artist: String,
    val source: String,
    val resolution: String,
    val previewUrl: String,
    val hdUrl: String
)

object HdCoverSearcher {
    private const val UA = "AmberMusic/2.9.0 (https://github.com/Liox269/ambermusic)"

    suspend fun search(query: String): List<HdCoverCandidate> = withContext(Dispatchers.IO) {
        val cleaned = query.trim()
        if (cleaned.isBlank()) return@withContext emptyList()
        var results = searchOnce(cleaned)
        val primaryCount = results.count { it.source != "MusicBrainz" }
        val words = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (primaryCount < 3 && words.size >= 3) {
            val shorter = words.dropLast(1).joinToString(" ")
            results = (results + searchOnce(shorter)).distinctBy { it.hdUrl }
        }
        results
    }

    private suspend fun searchOnce(query: String): List<HdCoverCandidate> = coroutineScope {
        val ytm = async { runCatching { searchYouTubeMusic(query) }.getOrDefault(emptyList()) }
        val itunes = async { runCatching { searchItunes(query) }.getOrDefault(emptyList()) }
        val deezer = async { runCatching { searchDeezer(query) }.getOrDefault(emptyList()) }
        val mb = async { runCatching { searchMusicBrainz(query) }.getOrDefault(emptyList()) }
        (ytm.await() + itunes.await() + deezer.await() + mb.await()).distinctBy { it.hdUrl }
    }

    private fun searchYouTubeMusic(query: String): List<HdCoverCandidate> {
        val body = JSONObject().apply {
            put("query", query)
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20241001.01.00")
                    put("hl", "en")
                    put("gl", "US")
                })
            })
        }.toString()
        val conn = (URL("https://music.youtube.com/youtubei/v1/search?prettyPrint=false").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 5000
            readTimeout = 7000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Origin", "https://music.youtube.com")
            setRequestProperty("Referer", "https://music.youtube.com/")
            setRequestProperty("User-Agent", UA)
        }
        val raw = try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode != 200) return emptyList()
            readBoundedText(conn, 2_000_000)
        } finally {
            conn.disconnect()
        }
        val regex = Regex("""https://lh3\.googleusercontent\.com/[^"\\\s]+""")
        val urls = regex.findAll(raw).map { it.value }.distinct().take(6).toList()
        return urls.mapIndexed { idx, original ->
            val base = original.substringBefore("=w").substringBefore("=s")
            HdCoverCandidate(
                title = if (idx == 0) query else "$query #${idx + 1}",
                artist = "YouTube Music",
                source = "YouTube Music",
                resolution = "1200×1200",
                previewUrl = "$base=w300-h300-l90-rj",
                hdUrl = "$base=w1200-h1200-l90-rj"
            )
        }
    }

    private fun searchItunes(query: String): List<HdCoverCandidate> {
        val url = Uri.parse("https://itunes.apple.com/search").buildUpon()
            .appendQueryParameter("media", "music")
            .appendQueryParameter("entity", "song")
            .appendQueryParameter("limit", "5")
            .appendQueryParameter("term", query)
            .build().toString()
        val raw = httpGet(url) ?: return emptyList()
        val results = JSONObject(raw).optJSONArray("results") ?: JSONArray()
        val out = ArrayList<HdCoverCandidate>()
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val art = item.optString("artworkUrl100").takeIf { it.isNotBlank() } ?: continue
            out.add(
                HdCoverCandidate(
                    title = item.optString("trackName").ifBlank { item.optString("collectionName", query) },
                    artist = item.optString("artistName"),
                    source = "iTunes",
                    resolution = "1200×1200",
                    previewUrl = art.replace("100x100bb", "300x300bb"),
                    hdUrl = art.replace("100x100bb", "1200x1200bb")
                )
            )
        }
        return out
    }

    private fun searchDeezer(query: String): List<HdCoverCandidate> {
        val url = Uri.parse("https://api.deezer.com/search").buildUpon()
            .appendQueryParameter("limit", "6")
            .appendQueryParameter("q", query)
            .build().toString()
        val raw = httpGet(url) ?: return emptyList()
        val data = JSONObject(raw).optJSONArray("data") ?: JSONArray()
        val out = ArrayList<HdCoverCandidate>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            val album = item.optJSONObject("album") ?: continue
            val hd = album.optString("cover_xl").ifBlank { album.optString("cover_big") }
            if (hd.isBlank()) continue
            val preview = album.optString("cover_medium").ifBlank { hd }
            val artist = item.optJSONObject("artist")?.optString("name").orEmpty()
            out.add(
                HdCoverCandidate(
                    title = item.optString("title", query),
                    artist = artist,
                    source = "Deezer",
                    resolution = "1000×1000",
                    previewUrl = preview,
                    hdUrl = hd
                )
            )
        }
        return out
    }

    private fun searchMusicBrainz(query: String): List<HdCoverCandidate> {
        val url = Uri.parse("https://musicbrainz.org/ws/2/release").buildUpon()
            .appendQueryParameter("fmt", "json")
            .appendQueryParameter("limit", "4")
            .appendQueryParameter("query", query)
            .build().toString()
        val raw = httpGet(url) ?: return emptyList()
        val releases = JSONObject(raw).optJSONArray("releases") ?: JSONArray()
        val out = ArrayList<HdCoverCandidate>()
        for (i in 0 until releases.length()) {
            val item = releases.optJSONObject(i) ?: continue
            val mbid = item.optString("id").takeIf { it.isNotBlank() } ?: continue
            val artistCredit = item.optJSONArray("artist-credit")?.optJSONObject(0)?.optString("name").orEmpty()
            out.add(
                HdCoverCandidate(
                    title = item.optString("title", query),
                    artist = artistCredit,
                    source = "MusicBrainz",
                    resolution = "1200×1200",
                    previewUrl = "https://coverartarchive.org/release/$mbid/front-250",
                    hdUrl = "https://coverartarchive.org/release/$mbid/front-1200"
                )
            )
        }
        return out
    }

    suspend fun downloadBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 6000
            readTimeout = 10000
            setRequestProperty("User-Agent", UA)
        }
        try {
            if (conn.responseCode !in 200..299) return@withContext null
            val out = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buf = ByteArray(8192)
                while (true) {
                    val read = input.read(buf)
                    if (read < 0) break
                    out.write(buf, 0, read)
                    if (out.size() > 6_000_000) return@withContext null
                }
            }
            out.toByteArray().takeIf { it.isNotEmpty() }
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun httpGet(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 5000
            readTimeout = 7000
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (conn.responseCode != 200) null else readBoundedText(conn, 1_500_000)
        } finally {
            conn.disconnect()
        }
    }

    private fun readBoundedText(conn: HttpURLConnection, limit: Int): String {
        val out = ByteArrayOutputStream()
        conn.inputStream.use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val read = input.read(buf)
                if (read < 0) break
                out.write(buf, 0, read)
                if (out.size() > limit) break
            }
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
