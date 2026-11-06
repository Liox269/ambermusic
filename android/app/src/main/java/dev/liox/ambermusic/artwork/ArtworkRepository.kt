package dev.liox.ambermusic.artwork

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import androidx.core.content.FileProvider
import dev.liox.ambermusic.data.local.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream

/**
 * Offline artwork cache shared by Media3, the library and the player.
 * Track-specific embedded art is preferred; system album art is a fallback.
 * The file is private to the app and is never downloaded from the network.
 */
class ArtworkRepository(private val context: Context) {
    private val directory by lazy { File(context.cacheDir, "covers").apply { mkdirs() } }
    private val fileMutex = Mutex()
    private val unavailable = mutableSetOf<String>()
    private val customDirectory by lazy { File(context.filesDir, "custom_art").apply { mkdirs() } }
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version

    suspend fun setCustom(songId: Long, uri: Uri) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            val bytes = context.contentResolver.openInputStream(uri)?.use { source ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = source.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    require(output.size() <= 6_000_000) { "Imagen demasiado grande" }
                }
                output.toByteArray()
            } ?: error("No se puede leer la imagen")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth in 1..8000 && bounds.outHeight in 1..8000) { "Imagen no válida" }
            val sample = sampleSize(bounds.outWidth, bounds.outHeight, 1200)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("No se pudo decodificar")
            try {
                val destination = File(customDirectory, "$songId.jpg")
                val temp = File(customDirectory, "$songId.part")
                try {
                    temp.outputStream().use { out ->
                        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out))
                    }
                    if (!temp.renameTo(destination)) temp.copyTo(destination, overwrite = true)
                } finally { temp.delete() }
                _version.value += 1
            } finally { bitmap.recycle() }
        }
    }

    suspend fun clearCustom(songId: Long) = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            File(customDirectory, "$songId.jpg").delete()
            _version.value += 1
        }
    }


    suspend fun fileFor(song: SongEntity): File? = withContext(Dispatchers.IO) {
        fileMutex.withLock { coverFile(song) }
    }

    suspend fun uriFor(song: SongEntity): Uri? = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            coverFile(song)?.let {
                FileProvider.getUriForFile(context, "${context.packageName}.artwork", it)
            }
        }
    }

    suspend fun load(song: SongEntity): ArtworkResult? = withContext(Dispatchers.IO) {
        fileMutex.withLock {
            val file = coverFile(song) ?: return@withLock null
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.artwork", file)
            ArtworkResult(uri, file.readBytes())
        }
    }

    private fun coverFile(song: SongEntity): File? {
        val chosen = File(customDirectory, "${song.id}.jpg")
        if (chosen.isFile && chosen.length() > 0L) return chosen
        val key = "${song.id}_${song.modifiedAtSeconds}_${song.albumId ?: 0L}"
        val file = File(directory, "$key.jpg")
        if (file.exists() && file.length() in 1..2_000_000L) return file
        if (key in unavailable) return null
        if (file.exists()) file.delete()

        val embedded = embeddedBitmap(song)
        val artwork = embedded ?: albumBitmap(song)
        if (artwork == null) {
            unavailable.add(key)
            return null
        }
        try {
            val scale = minOf(1f, 720f / maxOf(artwork.width, artwork.height))
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    artwork,
                    (artwork.width * scale).toInt().coerceAtLeast(1),
                    (artwork.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else artwork
            try {
                val temp = File(directory, "$key.tmp")
                try {
                    val written = temp.outputStream().use { out ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    }
                    if (!written || temp.length() == 0L) return null
                    if (!temp.renameTo(file)) {
                        temp.copyTo(file, overwrite = true)
                        temp.delete()
                    }
                } finally {
                    if (temp.exists()) temp.delete()
                }
            } finally {
                if (scaled !== artwork) scaled.recycle()
            }
        } catch (_: IOException) {
            unavailable.add(key)
            return null
        } finally {
            artwork.recycle()
        }
        return file.takeIf { it.length() in 1..2_000_000L }
    }

    private fun embeddedBitmap(song: SongEntity): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(song.contentUri))
            val bytes = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth < 1 || bounds.outHeight < 1) return null
            val sample = sampleSize(bounds.outWidth, bounds.outHeight, 1024)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
                inSampleSize = sample
            })
        } catch (_: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    private fun albumBitmap(song: SongEntity): Bitmap? {
        val id = song.albumId?.takeIf { it > 0L } ?: return null
        return try {
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, id)
            context.contentResolver.loadThumbnail(uri, Size(720, 720), null)
        } catch (_: Exception) {
            null
        }
    }

    private fun sampleSize(width: Int, height: Int, longestSide: Int): Int {
        var sample = 1
        while (maxOf(width / sample, height / sample) > longestSide && sample < 64) {
            sample *= 2
        }
        return sample
    }
}

data class ArtworkResult(val uri: Uri, val encodedJpeg: ByteArray)
