package dev.liox.ambermusic.profiles

import android.content.Context
import dev.liox.ambermusic.settings.AmberSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Local, offline profile registry. The original music database is not migrated or reset. */
data class LocalProfile(val id: String, val name: String, val avatar: String? = null)

class ProfileStore(context: Context, private val settings: AmberSettings) {
    private val prefs = context.applicationContext.getSharedPreferences("amber_profiles_v22", Context.MODE_PRIVATE)
    private val primary = LocalProfile("default", prefs.getString("default_name", "Mi perfil") ?: "Mi perfil", prefs.getString("default_avatar", "")?.takeIf { it.isNotBlank() })
    private val _profiles = MutableStateFlow(readProfiles())
    val profiles: StateFlow<List<LocalProfile>> = _profiles
    private val _active = MutableStateFlow(
        _profiles.value.firstOrNull { it.id == prefs.getString("active_profile", "default") } ?: primary
    )
    val active: StateFlow<LocalProfile> = _active
    private val _favoriteUris = MutableStateFlow(loadFavorites(_active.value.id))
    val favoriteUris: StateFlow<Set<String>> = _favoriteUris

    init { settings.activateProfile(_active.value.id) }

    private fun readProfiles(): List<LocalProfile> = runCatching {
        val raw = prefs.getString("profiles", "[]") ?: "[]"
        val parsed = JSONArray(raw)
        buildList {
            add(primary)
            for (index in 0 until minOf(parsed.length(), ProfileRules.MAX_PROFILES - 1)) {
                val value = parsed.optJSONObject(index) ?: continue
                val id = value.optString("id")
                val name = ProfileRules.safeName(value.optString("name"))
                if (id != "default" && ProfileRules.validIdentifier(id) && name.isNotBlank() && none { it.id == id }) {
                    add(LocalProfile(id, name, value.optString("avatar").takeIf { it.isNotBlank() }))
                }
            }
        }
    }.getOrDefault(listOf(primary))

    private fun persist() {
        val json = JSONArray()
        _profiles.value.filterNot { it.id == "default" }.forEach {
            json.put(JSONObject().put("id", it.id).put("name", it.name).put("avatar", it.avatar.orEmpty()))
        }
        prefs.edit().putString("profiles", json.toString()).apply()
    }

    private fun loadFavorites(id: String): Set<String> =
        prefs.getStringSet("favorites_$id", emptySet())?.toSet().orEmpty()

    fun add(name: String): Boolean {
        val safe = ProfileRules.safeName(name)
        if (safe.isBlank() || _profiles.value.size >= ProfileRules.MAX_PROFILES) return false
        val profile = LocalProfile(UUID.randomUUID().toString().replace("-", ""), safe)
        _profiles.value = _profiles.value + profile
        persist()
        select(profile.id)
        return true
    }

    fun rename(id: String, name: String): Boolean {
        val safe = ProfileRules.safeName(name)
        if (safe.isBlank()) return false
        _profiles.value = _profiles.value.map { if (it.id == id) it.copy(name = safe) else it }
        if (_active.value.id == id) _active.value = _profiles.value.first { it.id == id }
        if (id == "default") prefs.edit().putString("default_name", safe).apply()
        persist()
        return true
    }

    fun setAvatar(id: String, uri: String?) {
        _profiles.value = _profiles.value.map { if (it.id == id) it.copy(avatar = uri) else it }
        if (_active.value.id == id) _active.value = _profiles.value.first { it.id == id }
        if (id == "default") prefs.edit().putString("default_avatar", uri).apply()
        persist()
    }

    fun select(id: String) {
        val found = _profiles.value.firstOrNull { it.id == id } ?: return
        if (found.id == _active.value.id) return
        settings.activateProfile(found.id)
        _active.value = found
        _favoriteUris.value = loadFavorites(found.id)
        prefs.edit().putString("active_profile", found.id).apply()
    }

    fun delete(id: String): Boolean {
        if (id == "default" || _profiles.value.none { it.id == id }) return false
        if (_active.value.id == id) select("default")
        _profiles.value = _profiles.value.filterNot { it.id == id }
        prefs.edit().remove("favorites_$id").apply()
        persist()
        return true
    }

    /** Non-default favorites are isolated. The original profile continues using the existing Room favorites. */
    fun toggleFavorite(uri: String) {
        if (_active.value.id == "default" || uri.isBlank()) return
        val next = _favoriteUris.value.toMutableSet()
        if (!next.add(uri)) next.remove(uri)
        _favoriteUris.value = next
        prefs.edit().putStringSet("favorites_${_active.value.id}", next).apply()
    }

    fun addFavorites(uris: Set<String>) {
        if (_active.value.id == "default") return
        val next = _favoriteUris.value + uris.filter { it.isNotBlank() }
        _favoriteUris.value = next
        prefs.edit().putStringSet("favorites_${_active.value.id}", next).apply()
    }

    fun exportProfile(id: String, defaultFavoriteUris: Set<String> = emptySet()): String {
        val p = _profiles.value.firstOrNull { it.id == id } ?: error("Perfil no encontrado")
        return JSONObject().put("format", "amber-profile").put("version", 1)
            .put("name", p.name).put("avatar", p.avatar.orEmpty())
            .put("favorites", JSONArray(if (id == "default") defaultFavoriteUris.toList() else loadFavorites(id).toList()))
            .put("appearance", settings.appearanceForProfile(id)).toString(2)
    }

    fun importProfile(raw: String): Boolean {
        if (raw.length > 2_000_000 || _profiles.value.size >= ProfileRules.MAX_PROFILES) return false
        return runCatching {
            val json = JSONObject(raw)
            if (json.optString("format") != "amber-profile" || json.optInt("version") != 1) return false
            val name = ProfileRules.safeName(json.optString("name"))
            if (!ProfileRules.validName(name) || !add(name)) return false
            val targetId = _active.value.id
            val arr = json.optJSONArray("favorites") ?: JSONArray()
            val favorites = buildSet {
                for (i in 0 until minOf(arr.length(), 50000)) {
                    val uri = arr.optString(i)
                    if (uri.startsWith("content://") && uri.length <= 1000) add(uri)
                }
            }
            addFavorites(favorites)
            json.optJSONObject("appearance")?.let { settings.importAppearanceForProfile(targetId, it) }
            true
        }.getOrDefault(false)
    }
}
