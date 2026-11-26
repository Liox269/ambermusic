package dev.liox.ambermusic.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class DownloadAudioFormat(val ext: String, val mimeType: String, val label: String) {
    FLAC("flac", "audio/flac", "FLAC"),
    MP3("mp3", "audio/mpeg", "MP3"),
    OPUS("opus", "audio/ogg", "OPUS"),
    M4A("m4a", "audio/mp4", "M4A")
}

data class DownloadResult(
    val title: String,
    val displayName: String,
    val format: DownloadAudioFormat,
    val mediaUri: Uri
)

class UrlMusicDownloader(private val context: Context) {
    private val initMutex = Mutex()
    @Volatile
    private var initialized = false

    private suspend fun ensureInitialized() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            YoutubeDL.getInstance().init(context.applicationContext)
            FFmpeg.getInstance().init(context.applicationContext)
            initialized = true
        }
    }

    suspend fun download(
        rawInput: String,
        format: DownloadAudioFormat,
        onProgress: (Float, String) -> Unit
    ): DownloadResult = withContext(Dispatchers.IO) {
        val trimmed = rawInput.trim()
        require(trimmed.isNotBlank()) { "Ingresa una URL o nombre de canción" }

        onProgress(0.05f, "Inicializando motor de descarga…")
        ensureInitialized()

        onProgress(0.12f, "Resolviendo fuente de audio…")
        val target = resolveTarget(trimmed)

        val tmpDir = File(context.cacheDir, "ytdl_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val request = YoutubeDLRequest(target).apply {
                addOption("--extract-audio")
                addOption("--audio-format", format.ext)
                addOption("--audio-quality", "0")
                addOption("--embed-metadata")
                addOption("--embed-thumbnail")
                addOption("--add-metadata")
                addOption("--no-playlist")
                addOption("-o", "${tmpDir.absolutePath}/%(title).100B.%(ext)s")
            }

            onProgress(0.18f, "Descargando en ${format.label}…")
            YoutubeDL.getInstance().execute(request) { progress, _, _ ->
                if (progress >= 0f) {
                    val mapped = (0.18f + (progress / 100f) * 0.72f).coerceIn(0.18f, 0.90f)
                    onProgress(mapped, "Descargando ${progress.toInt()}% (${format.label})…")
                }
            }

            val outputFile = tmpDir.listFiles()
                ?.filter { it.isFile && it.length() > 0L }
                ?.sortedByDescending { if (it.extension.equals(format.ext, ignoreCase = true)) Long.MAX_VALUE else it.length() }
                ?.firstOrNull()
                ?: error("No se generó el archivo de audio")

            onProgress(0.93f, "Guardando en Música/AmberMusic…")
            val mediaUri = publishToMediaStore(outputFile, format)
            val title = outputFile.nameWithoutExtension
            onProgress(1f, "Descargado: $title")
            DownloadResult(
                title = title,
                displayName = outputFile.name,
                format = format,
                mediaUri = mediaUri
            )
        } finally {
            tmpDir.deleteRecursively()
        }
    }

    private fun resolveTarget(input: String): String {
        val lower = input.lowercase()
        val isUrl = lower.startsWith("http://") || lower.startsWith("https://")
        if (!isUrl) return "ytsearch1:$input"

        val needsMetadataLookup = lower.contains("open.spotify.com") ||
            lower.contains("music.apple.com") ||
            lower.contains("tidal.com") ||
            lower.contains("deezer.com")

        if (!needsMetadataLookup) return input

        val query = extractTitleFromWebPage(input)
        return if (!query.isNullOrBlank()) "ytsearch1:$query" else input
    }

    private fun extractTitleFromWebPage(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = 6000
            readTimeout = 8000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/125.0 Mobile Safari/537.36")
        }
        val html = try {
            if (conn.responseCode !in 200..299) return null
            val out = ByteArrayOutputStream()
            conn.inputStream.use { stream ->
                val buf = ByteArray(8192)
                while (true) {
                    val read = stream.read(buf)
                    if (read < 0) break
                    out.write(buf, 0, read)
                    if (out.size() > 250_000) break
                }
            }
            out.toString(Charsets.UTF_8.name())
        } catch (_: Exception) {
            return null
        } finally {
            conn.disconnect()
        }

        val ogTitle = Regex("""<meta[^>]+property=["']og:title["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)
            ?: Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+property=["']og:title["']""", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.getOrNull(1)
        val ogDesc = Regex("""<meta[^>]+property=["']og:description["'][^>]+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)
        val pageTitle = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.getOrNull(1)

        val raw = (ogTitle ?: pageTitle ?: return null)
            .replace("&amp;", "&")
            .replace("&#x27;", "'")
            .replace("&quot;", "\"")
            .replace(Regex("""\s*[\|\-–]\s*(Spotify|Apple Music|TIDAL|Deezer).*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*-\s*song and lyrics by\s*""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val artistHint = ogDesc
            ?.substringBefore("·")
            ?.replace(Regex("""Listen to .*? on Spotify\.?\s*""", RegexOption.IGNORE_CASE), "")
            ?.trim()
            ?.takeIf { it.isNotBlank() && it.length < 60 && !raw.contains(it, ignoreCase = true) }

        return if (artistHint != null) "$raw $artistHint" else raw
    }

    private fun publishToMediaStore(file: File, format: DownloadAudioFormat): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Audio.Media.MIME_TYPE, format.mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/AmberMusic")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("No se pudo registrar el archivo en MediaStore")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            } ?: error("No se pudo escribir el archivo en MediaStore")
            val done = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
            return uri
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }
}
