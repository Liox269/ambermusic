package dev.liox.ambermusic.playlists

import java.net.URLDecoder
import java.text.Normalizer
import java.util.Locale

/** Offline UTF-8 M3U8 format. No file writes or content:// URI assumptions here. */
data class M3uTrack(val path: String, val title: String? = null, val durationSeconds: Long? = null)
data class M3uImportResult(val matchedIds: List<Long>, val missing: List<String>, val ambiguous: List<String>)
data class LocalTrackPath(val id: Long, val folder: String, val fileName: String, val title: String, val durationMs: Long)

object M3uCodec {
    fun parse(input: String): List<M3uTrack> {
        var title: String? = null
        var duration: Long? = null
        val tracks = ArrayList<M3uTrack>()
        input.removePrefix("\uFEFF").lineSequence().forEach { raw ->
            val line = raw.trim()
            when {
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val meta = line.substringAfter(':')
                    duration = meta.substringBefore(',').trim().toLongOrNull()?.takeIf { it >= 0 }
                    title = meta.substringAfter(',', "").trim().takeIf { it.isNotEmpty() }
                }
                line.isEmpty() || line.startsWith('#') -> Unit
                else -> {
                    tracks += M3uTrack(line, title, duration)
                    title = null
                    duration = null
                }
            }
        }
        return tracks
    }

    /** Relative paths survive copying Music/ folders between Android and Linux. */
    fun export(tracks: List<LocalTrackPath>): String = buildString {
        append("#EXTM3U\n")
        for (song in tracks) {
            val safeTitle = song.title.replace('\n', ' ').replace('\r', ' ')
            append("#EXTINF:").append(song.durationMs / 1000L).append(',').append(safeTitle).append('\n')
            val relative = listOf(song.folder.trim('/'), song.fileName.trim('/'))
                .filter { it.isNotEmpty() }.joinToString("/")
            append(relative).append('\n')
        }
    }

    fun match(entries: List<M3uTrack>, library: List<LocalTrackPath>): M3uImportResult {
        // Only exact relative paths and unique filenames are matched. Ambiguous files are never guessed.
        val byPath = library.groupBy { normalize(it.folder.trimEnd('/') + "/" + it.fileName) }
        val byFilename = library.groupBy { normalize(it.fileName) }
        val matches = ArrayList<Long>()
        val missing = ArrayList<String>()
        val ambiguous = ArrayList<String>()
        for (entry in entries) {
            val path = normalize(entry.path)
            val candidates = byPath[path] ?: byPath.entries
                .filter { (known, _) -> path.endsWith("/" + known) || known.endsWith("/" + path) }
                .flatMap { it.value }.distinctBy { it.id }.takeIf { it.isNotEmpty() }
                ?: byFilename[path.substringAfterLast('/')].orEmpty()
            when (candidates.size) {
                0 -> missing += entry.path
                1 -> matches += candidates.first().id
                else -> ambiguous += entry.path
            }
        }
        return M3uImportResult(matches, missing, ambiguous)
    }

    private fun normalize(value: String): String {
        var text = value.trim().replace('\\', '/')
        if (text.startsWith("file://", ignoreCase = true)) {
            text = runCatching { URLDecoder.decode(text.substring(7), "UTF-8") }.getOrDefault(text.substring(7))
        }
        text = text.removePrefix("./").trimStart('/')
        val parts = ArrayList<String>()
        for (part in text.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += part
            }
        }
        return Normalizer.normalize(parts.joinToString("/"), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
    }
}
