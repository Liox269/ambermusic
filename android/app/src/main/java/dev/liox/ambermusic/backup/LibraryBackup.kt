package dev.liox.ambermusic.backup

import dev.liox.ambermusic.data.local.MusicDao
import dev.liox.ambermusic.smart.SmartPlaylistStore
import dev.liox.ambermusic.data.local.SongCategoryCrossRef
import dev.liox.ambermusic.data.local.SongPlaylistCrossRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Local JSON backup for user-maintained collections; intentionally excludes copyrighted audio files. */
class LibraryBackup(private val dao: MusicDao, private val smart: SmartPlaylistStore) {
    suspend fun export(): String = withContext(Dispatchers.IO) {
        val root = JSONObject().put("format", "amber-library-v1")
        val categories = dao.observeCategories().first()
        val playlists = dao.observePlaylists().first()
        val categoryLinks = dao.categoryLinks().groupBy { it.categoryId }
        val playlistLinks = dao.playlistLinks().groupBy { it.playlistId }
        root.put("categories", JSONArray().apply {
            categories.forEach { c ->
                put(JSONObject().put("name", c.name).put("songIds", JSONArray().apply {
                    categoryLinks[c.id].orEmpty().forEach { put(it.songId) }
                }))
            }
        })
        root.put("playlists", JSONArray().apply {
            playlists.forEach { p ->
                put(JSONObject().put("name", p.name).put("songIds", JSONArray().apply {
                    playlistLinks[p.id].orEmpty().forEach { put(it.songId) }
                }))
            }
        })
        root.put("favorites", JSONArray().apply { dao.favoriteRows().forEach { put(it.songId) } })
        root.put("smartPlaylists", JSONArray().apply {
            smart.rules.value.forEach { rule ->
                put(JSONObject().put("name", rule.name).put("type", rule.type).put("value", rule.value))
            }
        })
        root.toString(2)
    }

    suspend fun restore(json: String): Int = withContext(Dispatchers.IO) {
        require(json.length < 4_000_000) { "Respaldo demasiado grande" }
        val root = JSONObject(json)
        require(root.optString("format") == "amber-library-v1") { "Formato de respaldo desconocido" }
        var linked = 0
        val categories = root.optJSONArray("categories") ?: JSONArray()
        for (i in 0 until categories.length()) {
            val item = categories.getJSONObject(i)
            val name = item.getString("name").trim().take(80)
            if (name.isEmpty()) continue
            val id = dao.createCategory(name)
            val ids = item.optJSONArray("songIds") ?: JSONArray()
            for (j in 0 until ids.length()) {
                val songId = ids.optLong(j, -1)
                if (songId > 0 && dao.getSong(songId) != null) {
                    dao.insertSongCategory(SongCategoryCrossRef(songId, id))
                    linked++
                }
            }
        }
        val playlists = root.optJSONArray("playlists") ?: JSONArray()
        for (i in 0 until playlists.length()) {
            val item = playlists.getJSONObject(i)
            val name = item.getString("name").trim().take(80)
            if (name.isEmpty()) continue
            val id = dao.createPlaylist(name)
            val ids = item.optJSONArray("songIds") ?: JSONArray()
            for (j in 0 until ids.length()) {
                val songId = ids.optLong(j, -1)
                if (songId > 0 && dao.getSong(songId) != null) {
                    dao.appendSongToPlaylist(id, songId)
                    linked++
                }
            }
        }
        val favorites = root.optJSONArray("favorites") ?: JSONArray()
        for (i in 0 until favorites.length()) {
            val songId = favorites.optLong(i, -1)
            if (songId > 0 && dao.getSong(songId) != null) {
                dao.setFavorite(songId, true)
                linked++
            }
        }
        val smartRules = root.optJSONArray("smartPlaylists") ?: JSONArray()
        for (i in 0 until smartRules.length()) {
            val row = smartRules.optJSONObject(i) ?: continue
            val name = row.optString("name").take(80)
            val type = row.optString("type")
            val value = row.optString("value").take(100)
            runCatching {
                if (smart.rules.value.none { it.name == name && it.type == type && it.value == value }) {
                    smart.add(name, type, value)
                }
            }
        }
        linked
    }
}
