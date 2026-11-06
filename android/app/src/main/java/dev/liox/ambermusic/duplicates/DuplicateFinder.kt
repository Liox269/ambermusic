package dev.liox.ambermusic.duplicates

import android.content.Context
import android.net.Uri
import dev.liox.ambermusic.data.local.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** On-demand exact duplicate detection. A matching name/duration alone is never treated as proof. */
class DuplicateFinder(private val context: Context) {
    suspend fun analyze(songs: List<SongEntity>, progress: (Int, Int) -> Unit): List<List<SongEntity>> =
        withContext(Dispatchers.IO) {
            val candidates = songs.filter { it.isVisible && it.sizeBytes > 0 }
                .groupBy { it.sizeBytes }.values.filter { it.size > 1 }.flatten()
            val hashes = LinkedHashMap<Pair<Long, String>, MutableList<SongEntity>>()
            candidates.forEachIndexed { index, song ->
                coroutineContext.ensureActive()
                try {
                    val digest = MessageDigest.getInstance("SHA-256")
                    context.contentResolver.openInputStream(Uri.parse(song.contentUri))?.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            digest.update(buffer, 0, n)
                        }
                        val key = song.sizeBytes to digest.digest().joinToString("") { "%02x".format(it) }
                        hashes.getOrPut(key) { mutableListOf() }.add(song)
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A revoked URI is skipped; files must never be modified or deleted.
                }
                progress(index + 1, candidates.size)
            }
            hashes.values.filter { it.size > 1 }.map { it.toList() }
        }
}
