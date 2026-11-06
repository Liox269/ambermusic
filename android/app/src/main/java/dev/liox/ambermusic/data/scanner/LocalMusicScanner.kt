package dev.liox.ambermusic.data.scanner

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import dev.liox.ambermusic.data.local.MusicDao
import dev.liox.ambermusic.data.local.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.coroutineContext

class LocalMusicScanner(
    private val context: Context,
    private val dao: MusicDao,
    private val extractor: AudioMetadataExtractor,
    private val folders: MusicFolderSettings
) {
    private val scanMutex = Mutex()

    companion object {
        const val MIN_SONG_DURATION_MS = 60_000L
    }

    data class ScanReport(
        val addedOrUpdated: Int,
        val unchanged: Int,
        val unsupported: Int,
        val errors: Int,
        val removed: Int,
        val fullyReconciled: Boolean
    )

    fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun scan(): ScanReport = withContext(Dispatchers.IO) {
        scanMutex.withLock {
            check(hasAudioPermission()) { "Permiso de lectura de audio no concedido" }

            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val allowedFolders = folders.selectedFolders.value
            val scanPrefs = context.getSharedPreferences("amber_scanner", Context.MODE_PRIVATE)
            val refreshGenreOnce = !scanPrefs.getBoolean("genres_indexed", false)
            val refreshTracksOnce = !scanPrefs.getBoolean("track_numbers_indexed_v5", false)
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.Audio.AudioColumns.TITLE,
                MediaStore.Audio.AudioColumns.ARTIST,
                MediaStore.Audio.AudioColumns.ALBUM,
                MediaStore.Audio.AudioColumns.ALBUM_ID,
                MediaStore.Audio.AudioColumns.DURATION,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.MIME_TYPE
            )
            // Negative elapsedRealtimeNanos() cannot occur in practical device uptime.
            val token = SystemClock.elapsedRealtimeNanos()
            var changed = 0
            var unchanged = 0
            var unsupported = 0
            var errors = 0
            val cursor = context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Audio.Media._ID} ASC"
            ) ?: throw IOException("MediaStore devolvió un cursor nulo; se conserva la biblioteca anterior")

            cursor.use { rows ->
                val columns = Columns(rows)
                while (rows.moveToNext()) {
                    coroutineContext.ensureActive()
                    try {
                        val file = columns.read(rows)
                        if (!folders.isIncluded(file.folderPath, allowedFolders)) continue
                        if (!supports(file.name, file.mime)) {
                            unsupported++
                            continue
                        }
                        val contentUri = ContentUris.withAppendedId(collection, file.id)
                        val existing = dao.getSong(file.id)
                        if (existing != null && existing.durationMs <= MIN_SONG_DURATION_MS &&
                            existing.modifiedAtSeconds == file.modifiedAtSeconds &&
                            existing.sizeBytes == file.sizeBytes
                        ) continue
                        if (!refreshGenreOnce && !refreshTracksOnce && existing != null && existing.contentUri == contentUri.toString() &&
                            existing.modifiedAtSeconds == file.modifiedAtSeconds &&
                            existing.sizeBytes == file.sizeBytes
                        ) {
                            dao.markSongSeen(file.id, token)
                            unchanged++
                            continue
                        }
                        val technical = extractor.read(
                            uri = contentUri,
                            fileName = file.name,
                            mimeType = file.mime,
                            bytes = file.sizeBytes,
                            catalogDurationMs = file.durationMs
                        )
                        val confirmedDurationMs = technical.durationMs?.takeIf { it > 0L }
                            ?: file.durationMs
                        if (confirmedDurationMs <= MIN_SONG_DURATION_MS) continue
                        val rawArtist = technical.artist?.trim().orEmpty()
                            .ifBlank { file.artist.ifBlank { "Artista desconocido" } }
                        val names = ArtistNameParser.parse(rawArtist)
                        val albumTitle = technical.album?.trim().orEmpty()
                            .ifBlank { file.album.ifBlank { "Álbum desconocido" } }
                        val albumKey = file.albumId?.takeIf { it > 0L }?.let { "media:$it" }
                            ?: "local:${albumTitle.lowercase(Locale.ROOT)}:${names.first().lowercase(Locale.ROOT)}"
                        val resolvedTitle = technical.title?.trim().orEmpty()
                            .ifBlank { file.title.ifBlank { file.name.substringBeforeLast('.', file.name) } }
                        val displayOverride = dao.getOverride(file.id)
                        val finalArtist = displayOverride?.artist ?: names.joinToString(", ")
                        val finalNames = if (displayOverride == null) names else ArtistNameParser.parse(finalArtist)
                        dao.saveScannedSong(
                            SongEntity(
                                id = file.id,
                                contentUri = contentUri.toString(),
                                displayName = file.name,
                                title = displayOverride?.title ?: resolvedTitle,
                                artistDisplay = finalArtist,
                                albumKey = albumKey,
                                albumTitle = displayOverride?.album ?: albumTitle,
                                albumId = file.albumId,
                                folderPath = file.folderPath,
                                durationMs = confirmedDurationMs,
                                sizeBytes = file.sizeBytes,
                                modifiedAtSeconds = file.modifiedAtSeconds,
                                mimeType = file.mime,
                                format = technical.format,
                                bitrateBps = technical.bitrateBps,
                                bitrateSource = technical.bitrateSource,
                                sampleRateHz = technical.sampleRateHz,
                                bitDepth = technical.bitDepth,
                                scanToken = token,
                                genre = technical.genre.orEmpty(),
                                trackNumber = technical.trackNumber ?: 0,
                                discNumber = technical.discNumber ?: 0
                            ),
                            artistNames = finalNames
                        )
                        changed++
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (security: SecurityException) {
                        // Never purge the DB after a permission/storage failure.
                        throw security
                    } catch (failure: Exception) {
                        Log.w("LocalMusicScanner", "No fue posible indexar un archivo", failure)
                        errors++
                    }
                }
            }

            // Other folders are hidden in Room, never deleted: favorites and categories survive.
            val removed = if (errors == 0) dao.finishFullScan(token) else 0
            if (errors == 0) scanPrefs.edit()
                .putBoolean("genres_indexed", true)
                .putBoolean("track_numbers_indexed_v5", true)
                .apply()
            ScanReport(changed, unchanged, unsupported, errors, removed, errors == 0)
        }
    }

    suspend fun listAvailableFolders(): List<String> = withContext(Dispatchers.IO) {
        check(hasAudioPermission()) { "Debes permitir el acceso a música" }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val options = sortedSetOf(String.CASE_INSENSITIVE_ORDER)
        val cursor = context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns.RELATIVE_PATH),
            null, null, null
        ) ?: throw IOException("No se pudieron leer las carpetas de audio")
        cursor.use { rows ->
            val index = rows.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            while (rows.moveToNext()) {
                coroutineContext.ensureActive()
                if (!rows.isNull(index)) {
                    MusicFolderSettings.normalizedPath(rows.getString(index))?.let(options::add)
                }
            }
        }
        options.toList()
    }

    private fun supports(name: String, mime: String?): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return extension in setOf("mp3", "flac", "m4a", "wav", "wave", "ogg", "oga", "opus", "aac") ||
            mime?.lowercase(Locale.ROOT) in setOf(
                "audio/mpeg", "audio/mp3", "audio/flac", "audio/x-flac",
                "audio/mp4", "audio/x-m4a", "audio/wav", "audio/x-wav",
                "audio/wave", "audio/ogg", "audio/opus", "audio/aac"
            )
    }

    private data class AudioRow(
        val id: Long,
        val name: String,
        val title: String,
        val artist: String,
        val album: String,
        val albumId: Long?,
        val durationMs: Long,
        val sizeBytes: Long,
        val modifiedAtSeconds: Long,
        val folderPath: String,
        val mime: String?
    )

    private class Columns(cursor: Cursor) {
        private val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        private val name = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
        private val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.TITLE)
        private val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ARTIST)
        private val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ALBUM)
        private val albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.ALBUM_ID)
        private val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.AudioColumns.DURATION)
        private val size = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
        private val modified = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
        private val folder = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
        private val mime = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)

        fun read(cursor: Cursor): AudioRow = AudioRow(
            id = cursor.getLong(id),
            name = cursor.stringAt(name).orEmpty(),
            title = cursor.stringAt(title).orEmpty().takeUnless { it == "<unknown>" }.orEmpty(),
            artist = cursor.stringAt(artist).orEmpty().takeUnless { it == "<unknown>" }.orEmpty(),
            album = cursor.stringAt(album).orEmpty().takeUnless { it == "<unknown>" }.orEmpty(),
            albumId = if (cursor.isNull(albumId)) null else cursor.getLong(albumId),
            durationMs = cursor.longAt(duration),
            sizeBytes = cursor.longAt(size),
            modifiedAtSeconds = cursor.longAt(modified),
            folderPath = cursor.stringAt(folder).orEmpty(),
            mime = cursor.stringAt(mime)
        )

        private fun Cursor.stringAt(index: Int): String? = if (isNull(index)) null else getString(index)
        private fun Cursor.longAt(index: Int): Long = if (isNull(index)) 0L else getLong(index)
    }
}
