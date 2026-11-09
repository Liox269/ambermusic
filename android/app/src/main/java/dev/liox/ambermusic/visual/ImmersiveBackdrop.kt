package dev.liox.ambermusic.visual

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.palette.graphics.Palette
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Ambient light extracted strictly from a local cover.
 *
 * Version 2 deliberately does NOT enlarge a 96x96 JPEG: that produced visible square blocks.
 * One cached 256x550 blurred WebP is generated per cover, off the main thread, with a separable
 * two-pass box Gaussian approximation and clamped edges. Compose only fades cached bitmaps;
 * there is no expensive full-screen RenderEffect recomputed every frame.
 */
data class AmbientArtwork(val file: File, val primary: Int, val secondary: Int, val dark: Int)

class ImmersiveBackdrop(context: Context) {
    private val directory = File(context.applicationContext.cacheDir, "ambient-v3-lite")
    private val mutex = Mutex()

    suspend fun forCover(cover: File?): AmbientArtwork? = withContext(Dispatchers.IO) {
        if (cover?.isFile != true || cover.length() == 0L) return@withContext null
        mutex.withLock {
            directory.mkdirs()
            val key = MessageDigest.getInstance("SHA-256")
                .digest("v3-lite:${cover.absolutePath}:${cover.length()}:${cover.lastModified()}".toByteArray())
                .take(12).joinToString("") { "%02x".format(it) }
            val result = File(directory, "$key.webp")
            val paletteFile = File(directory, "$key.colors")
            if (result.isFile && paletteFile.isFile) {
                val colors = runCatching {
                    paletteFile.readText().split(',').map { it.toInt() }
                }.getOrNull()
                if (colors?.size == 3) {
                    return@withLock AmbientArtwork(result, colors[0], colors[1], colors[2])
                }
            }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(cover.absolutePath, bounds)
            if (bounds.outWidth < 1 || bounds.outHeight < 1) return@withLock null
            val maxEdge = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (maxEdge / (sample * 2) >= 540) sample *= 2
            val decoded = BitmapFactory.decodeFile(cover.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }) ?: return@withLock null
            try {
                val palette = Palette.from(decoded).maximumColorCount(12).generate()
                val primary = palette.vibrantSwatch?.rgb
                    ?: palette.dominantSwatch?.rgb ?: 0xFF1D3551.toInt()
                val secondary = palette.mutedSwatch?.rgb ?: primary
                val dark = palette.darkMutedSwatch?.rgb ?: 0xFF101923.toInt()

                // Portrait ambient field: album colors fill the edges; the sharp cover stays intact above it.
                val width = 256
                val height = 550
                val working = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(working)
                    canvas.drawBitmap(decoded, null, Rect(0, 0, width, height),
                        Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
                    val pixels = IntArray(width * height)
                    working.getPixels(pixels, 0, width, 0, 0, width, height)
                    // Blur runs on a computation dispatcher and is never scheduled per frame.
                    withContext(Dispatchers.Default) {
                        AmbientBlurKernel.blurInPlace(pixels, width, height, radius = 20, passes = 2)
                    }
                    working.setPixels(pixels, 0, width, 0, 0, width, height)
                    val temp = File(directory, "$key.tmp")
                    try {
                        val ok = temp.outputStream().use {
                            working.compress(Bitmap.CompressFormat.WEBP_LOSSY, 84, it)
                        }
                        if (!ok || temp.length() == 0L) return@withLock null
                        if (!temp.renameTo(result)) {
                            temp.copyTo(result, overwrite = true)
                            temp.delete()
                        }
                        paletteFile.writeText("$primary,$secondary,$dark")
                    } finally {
                        temp.delete()
                    }
                    // Each color cache consists of two files. Bound the on-disk cache.
                    val colors = directory.listFiles()?.filter { it.extension == "colors" }
                        ?.sortedByDescending { it.lastModified() }.orEmpty()
                    colors.drop(64).forEach { old ->
                        File(directory, "${old.nameWithoutExtension}.webp").delete()
                        old.delete()
                    }
                    AmbientArtwork(result, primary, secondary, dark)
                } finally {
                    working.recycle()
                }
            } finally {
                decoded.recycle()
            }
        }
    }

}
