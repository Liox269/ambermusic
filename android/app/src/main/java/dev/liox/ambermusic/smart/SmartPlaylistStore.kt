package dev.liox.ambermusic.smart

import android.content.Context
import dev.liox.ambermusic.data.local.SongEntity
import dev.liox.ambermusic.data.local.SongUserStateEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Dynamic playlists do not duplicate media or change original file metadata. */
data class SmartPlaylist(
    val id: String,
    val name: String,
    val type: String,
    val value: String = ""
)

object SmartRules {
    const val ARTIST = "ARTIST"
    const val ALBUM = "ALBUM"
    const val FOLDER = "FOLDER"
    const val FORMAT = "FORMAT"
    const val GENRE = "GENRE"
    const val FAVOURITES = "FAVOURITES"
    const val POPULAR = "POPULAR"
    const val RECENT = "RECENT"

    fun matches(rule: SmartPlaylist, song: SongEntity, state: SongUserStateEntity?): Boolean {
        if (!song.isVisible || song.durationMs <= 60_000) return false
        val value = rule.value.trim()
        return when (rule.type) {
            ARTIST -> value.isNotEmpty() && song.artistDisplay.contains(value, true)
            ALBUM -> value.isNotEmpty() && song.albumTitle.contains(value, true)
            FOLDER -> value.isNotEmpty() && song.folderPath.contains(value, true)
            FORMAT -> value.isNotEmpty() && song.format.equals(value, true)
            GENRE -> value.isNotEmpty() && song.genre.contains(value, true)
            FAVOURITES -> state?.isFavorite == true
            POPULAR -> (state?.playCount ?: 0L) >= (value.toLongOrNull()?.coerceAtLeast(1) ?: 3L)
            RECENT -> (state?.lastPlayedAtEpochMs ?: 0L) >= System.currentTimeMillis() - 30L * 86400_000L
            else -> false
        }
    }
}

class SmartPlaylistStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("amber_smart_lists", Context.MODE_PRIVATE)
    private val _rules = MutableStateFlow(read())
    val rules: StateFlow<List<SmartPlaylist>> = _rules

    fun add(name: String, type: String, value: String) {
        require(name.trim().isNotEmpty() && name.length <= 80)
        require(type in setOf(SmartRules.ARTIST, SmartRules.ALBUM, SmartRules.FOLDER,
            SmartRules.FORMAT, SmartRules.GENRE, SmartRules.FAVOURITES, SmartRules.POPULAR, SmartRules.RECENT))
        val rule = SmartPlaylist(UUID.randomUUID().toString(), name.trim(), type, value.take(100))
        save(_rules.value + rule)
    }

    fun remove(id: String) { save(_rules.value.filterNot { it.id == id }) }

    private fun read(): List<SmartPlaylist> = runCatching {
        val raw = JSONArray(prefs.getString("rules", "[]") ?: "[]")
        (0 until raw.length()).mapNotNull { index ->
            val row = raw.optJSONObject(index) ?: return@mapNotNull null
            SmartPlaylist(row.getString("id"), row.getString("name"), row.getString("type"), row.optString("value"))
        }
    }.getOrDefault(emptyList())

    private fun save(rules: List<SmartPlaylist>) {
        val array = JSONArray()
        rules.forEach { rule -> array.put(JSONObject().put("id", rule.id).put("name", rule.name)
            .put("type", rule.type).put("value", rule.value)) }
        prefs.edit().putString("rules", array.toString()).apply()
        _rules.value = rules
    }
}
