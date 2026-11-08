package dev.liox.ambermusic.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import dev.liox.ambermusic.profiles.ProfileRules

/** Small settings store: no network, no polling and no database migration. */
class AmberSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("amber_settings", Context.MODE_PRIVATE)
    // One-time visual reset for the reference redesign. Audio, favorites, lists and Room remain unchanged.
    init {
        if (prefs.getInt("redesign_revision", 0) < 240) {
            prefs.edit().putInt("redesign_revision", 240)
                .putString("theme", "coral")
                .putString("visual_style", "immersive")
                .putString("font_choice", "system")
                .putString("accent_hex", "")
                .putString("custom_background", "")
                .putString("density", "compact")
                .putBoolean("fit_cover", false)
                .apply()
        }
    }
    // The v2.6 player enables its requested album blur once; user can disable it later.
    init {
        if (prefs.getInt("player_blur_revision", 0) < 260) {
            prefs.edit().putInt("player_blur_revision", 260)
                .putBoolean("immersive", true).apply()
        }
    }
    private var activeProfileId = "default"
    private fun profileKey(key: String, id: String = activeProfileId): String =
        if (id == "default") key else "profile_${id}_$key"
    private val _accentHex = MutableStateFlow(prefs.getString("accent_hex", "") ?: "")
    val accentHex: StateFlow<String> = _accentHex
    private val _customBackground = MutableStateFlow(prefs.getString("custom_background", "") ?: "")
    val customBackground: StateFlow<String> = _customBackground
    private val _wallpaperOpacity = MutableStateFlow(prefs.getFloat("wallpaper_opacity", .65f).coerceIn(.1f, 1f))
    val wallpaperOpacity: StateFlow<Float> = _wallpaperOpacity
    private val _customThemeName = MutableStateFlow(prefs.getString("custom_theme_name", "Mi tema") ?: "Mi tema")
    val customThemeName: StateFlow<String> = _customThemeName
    private val _corners = MutableStateFlow(prefs.getString("corners", "rounded") ?: "rounded")
    val corners: StateFlow<String> = _corners
    private val _fontChoice = MutableStateFlow(prefs.getString("font_choice", "system") ?: "system")
    val fontChoice: StateFlow<String> = _fontChoice
    private val _iconStyle = MutableStateFlow(prefs.getString("icon_style", "filled") ?: "filled")
    val iconStyle: StateFlow<String> = _iconStyle
    private val _theme = MutableStateFlow(dev.liox.ambermusic.ui.AmberPalettes.fromId(prefs.getString("theme", "coral") ?: "coral").id)
    private val _offload = MutableStateFlow(prefs.getBoolean("offload", false))
    val theme: StateFlow<String> = _theme
    val offload: StateFlow<Boolean> = _offload
    private val _replayGain = MutableStateFlow(prefs.getString("replayGain", "off") ?: "off")
    val replayGain: StateFlow<String> = _replayGain
    private val _smoothTransition = MutableStateFlow(prefs.getBoolean("smoothTransition", false))
    val smoothTransition: StateFlow<Boolean> = _smoothTransition
    private val _immersive = MutableStateFlow(prefs.getBoolean("immersive", true))
    val immersive: StateFlow<Boolean> = _immersive
    private val _visualStyle = MutableStateFlow(prefs.getString("visual_style", "immersive") ?: "immersive")
    val visualStyle: StateFlow<String> = _visualStyle
    private val _density = MutableStateFlow(prefs.getString("density", "comfortable") ?: "comfortable")
    val density: StateFlow<String> = _density
    private val _reduceMotion = MutableStateFlow(prefs.getBoolean("reduce_motion", false))
    val reduceMotion: StateFlow<Boolean> = _reduceMotion
    private val _fitCover = MutableStateFlow(prefs.getBoolean("fit_cover", false))
    val fitCover: StateFlow<Boolean> = _fitCover
    // A second decoder is created only during an enabled crossfade window.
    private val _crossfadeSeconds = MutableStateFlow(prefs.getInt("crossfade_seconds", 0).coerceIn(0, 10))
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds
    private val _performanceProfile = MutableStateFlow(prefs.getString("performance_profile", "balanced") ?: "balanced")
    val performanceProfile: StateFlow<String> = _performanceProfile
    private val _restoreSession = MutableStateFlow(prefs.getBoolean("restore_session", true))
    val restoreSession: StateFlow<Boolean> = _restoreSession
    private val _previousBehavior = MutableStateFlow(prefs.getString("previous_behavior", "restart") ?: "restart")
    val previousBehavior: StateFlow<String> = _previousBehavior
    fun setRestoreSession(value: Boolean) {
        _restoreSession.value = value
        prefs.edit().putBoolean("restore_session", value).apply()
    }
    fun setPreviousBehavior(value: String) {
        val safe = value.takeIf { it == "restart" || it == "previous" } ?: "restart"
        _previousBehavior.value = safe
        prefs.edit().putString("previous_behavior", safe).apply()
    }

    fun setCrossfadeSeconds(value: Int) {
        val safe = value.coerceIn(0, 10)
        _crossfadeSeconds.value = safe
        prefs.edit().putInt("crossfade_seconds", safe).apply()
    }

    fun setPerformanceProfile(value: String) {
        val safe = value.takeIf { it in setOf("saver", "balanced", "fluid") } ?: "balanced"
        _performanceProfile.value = safe
        prefs.edit().putString("performance_profile", safe).apply()
        if (safe == "saver") setReduceMotion(true)
        else setReduceMotion(false)
        // Never force a global screen refresh rate, including the "fluid" profile.
    }

    fun setVisualStyle(value: String) {
        val safe = value.takeIf { it in setOf("amoled", "immersive", "cards", "light") } ?: "immersive"
        _visualStyle.value = safe
        prefs.edit().putString(profileKey("visual_style"), safe).apply()
    }
    fun setDensity(value: String) {
        val safe = value.takeIf { it in setOf("comfortable", "compact") } ?: "comfortable"
        _density.value = safe
        prefs.edit().putString(profileKey("density"), safe).apply()
    }
    fun setReduceMotion(value: Boolean) {
        _reduceMotion.value = value
        prefs.edit().putBoolean(profileKey("reduce_motion"), value).apply()
    }
    fun setFitCover(value: Boolean) {
        _fitCover.value = value
        prefs.edit().putBoolean(profileKey("fit_cover"), value).apply()
    }
    private val _autoOutputProfiles = MutableStateFlow(prefs.getBoolean("auto_output_profiles", false))
    val autoOutputProfiles: StateFlow<Boolean> = _autoOutputProfiles
    // Profiles are only replay gain modes, not unverified DAC or Bluetooth bitrate claims.
    private val _routePresets = MutableStateFlow(readRoutePresets())
    val routePresets: StateFlow<Map<String, String>> = _routePresets

    private fun readRoutePresets(): Map<String, String> = runCatching {
        val obj = org.json.JSONObject(prefs.getString("route_presets", "{}") ?: "{}")
        obj.keys().asSequence().associateWith { key -> obj.optString(key) }
            .filterValues { it in setOf("off", "track", "album") }
    }.getOrDefault(emptyMap())

    fun setAutoOutputProfiles(value: Boolean) {
        _autoOutputProfiles.value = value
        prefs.edit().putBoolean("auto_output_profiles", value).apply()
    }

    fun setRouteReplayGain(routeName: String, mode: String) {
        if (routeName.isBlank() || mode !in setOf("off", "track", "album")) return
        val updated = _routePresets.value.toMutableMap().apply { put(routeName.take(100), mode) }
        val obj = org.json.JSONObject()
        updated.forEach { (key, value) -> obj.put(key, value) }
        _routePresets.value = updated
        prefs.edit().putString("route_presets", obj.toString()).apply()
    }

    fun replayGainForRoute(name: String): String = OutputGainPolicy.resolve(
        _replayGain.value, _autoOutputProfiles.value, name, _routePresets.value)

    // Hidden duplicates are stored by MediaStore URI, not song title.
    // Originals stay on the phone and Room relationships are untouched.
    private val _hiddenDuplicateUris = MutableStateFlow(
        prefs.getStringSet("hiddenDuplicateUris", emptySet())?.toSet().orEmpty()
    )
    val hiddenDuplicateUris: StateFlow<Set<String>> = _hiddenDuplicateUris

    fun setHiddenDuplicateUris(uris: Set<String>) {
        val snapshot = uris.toSet()
        prefs.edit().putStringSet("hiddenDuplicateUris", snapshot).apply()
        _hiddenDuplicateUris.value = snapshot
    }


    fun setTheme(value: String) {
        val safe = dev.liox.ambermusic.ui.AmberPalettes.fromId(value).id
        _theme.value = safe
        prefs.edit().putString(profileKey("theme"), safe).apply()
    }

    fun setReplayGain(value: String) {
        val mode = value.takeIf { it in setOf("off", "track", "album") } ?: "off"
        _replayGain.value = mode
        prefs.edit().putString("replayGain", mode).apply()
    }

    fun setSmoothTransition(enabled: Boolean) {
        _smoothTransition.value = enabled
        prefs.edit().putBoolean("smoothTransition", enabled).apply()
    }

    fun setImmersive(enabled: Boolean) {
        _immersive.value = enabled
        prefs.edit().putBoolean(profileKey("immersive"), enabled).apply()
    }

    fun setOffload(enabled: Boolean) {
        _offload.value = enabled
        prefs.edit().putBoolean("offload", enabled).apply()
    }

    /** A profile changes only visual preferences; audio queue and library are never reset. */
    fun activateProfile(id: String) {
        require(ProfileRules.validIdentifier(id))
        activeProfileId = id
        _theme.value = dev.liox.ambermusic.ui.AmberPalettes.fromId(
            prefs.getString(profileKey("theme"), if (id == "default") "coral" else "cherry") ?: "cherry").id
        _visualStyle.value = prefs.getString(profileKey("visual_style"), "immersive") ?: "immersive"
        _density.value = prefs.getString(profileKey("density"), "comfortable") ?: "comfortable"
        _reduceMotion.value = prefs.getBoolean(profileKey("reduce_motion"), false)
        _fitCover.value = prefs.getBoolean(profileKey("fit_cover"), false)
        _immersive.value = prefs.getBoolean(profileKey("immersive"), true)
        _accentHex.value = prefs.getString(profileKey("accent_hex"), "") ?: ""
        _customBackground.value = prefs.getString(profileKey("custom_background"), "") ?: ""
        _wallpaperOpacity.value = prefs.getFloat(profileKey("wallpaper_opacity"), .65f).coerceIn(.1f, 1f)
        _customThemeName.value = prefs.getString(profileKey("custom_theme_name"), "Mi tema") ?: "Mi tema"
        _corners.value = prefs.getString(profileKey("corners"), "rounded") ?: "rounded"
        _fontChoice.value = prefs.getString(profileKey("font_choice"), "system") ?: "system"
        _iconStyle.value = prefs.getString(profileKey("icon_style"), "filled") ?: "filled"
    }

    fun setAccentHex(value: String) {
        val safe = ProfileRules.hexColorOrNull(value).orEmpty()
        _accentHex.value = safe
        prefs.edit().putString(profileKey("accent_hex"), safe).apply()
    }
    fun setCustomBackground(value: String) {
        // Only Android provider URIs or app-private files can be persisted.
        val safe = value.takeIf { it.length <= 2048 &&
            (it.startsWith("content://") || it.startsWith("file://") || it.isBlank()) }.orEmpty()
        _customBackground.value = safe
        prefs.edit().putString(profileKey("custom_background"), safe).apply()
    }
    fun setWallpaperOpacity(value: Float) {
        val safe = value.coerceIn(.1f, 1f)
        _wallpaperOpacity.value = safe
        prefs.edit().putFloat(profileKey("wallpaper_opacity"), safe).apply()
    }
    fun setCustomThemeName(value: String) {
        val safe = ProfileRules.safeName(value).ifBlank { "Mi tema" }
        _customThemeName.value = safe
        prefs.edit().putString(profileKey("custom_theme_name"), safe).apply()
    }
    fun setCorners(value: String) {
        val safe = if (value == "square") "square" else "rounded"
        _corners.value = safe
        prefs.edit().putString(profileKey("corners"), safe).apply()
    }
    fun setIconStyle(value: String) {
        val safe = if (value == "outlined") "outlined" else "filled"
        _iconStyle.value = safe
        prefs.edit().putString(profileKey("icon_style"), safe).apply()
    }
    fun setFontChoice(value: String) {
        val safe = if (value == "expressive") "expressive" else "system"
        _fontChoice.value = safe
        prefs.edit().putString(profileKey("font_choice"), safe).apply()
    }
    fun appearanceForProfile(id: String): JSONObject = JSONObject()
        .put("style", prefs.getString(profileKey("visual_style", id), "immersive"))
        .put("palette", prefs.getString(profileKey("theme", id), "cherry"))
        .put("accent", prefs.getString(profileKey("accent_hex", id), ""))
        .put("corners", prefs.getString(profileKey("corners", id), "rounded"))
        .put("opacity", prefs.getFloat(profileKey("wallpaper_opacity", id), .65f))
        .put("font", prefs.getString(profileKey("font_choice", id), "system"))
        .put("icons", prefs.getString(profileKey("icon_style", id), "filled"))
        .put("name", prefs.getString(profileKey("custom_theme_name", id), "Mi tema"))
        // Background provider URIs cannot be exported portably; import never trusts them.

    fun exportAppearance(): String = JSONObject()
        .put("format", "amber-theme").put("version", 1)
        .put("appearance", appearanceForProfile(activeProfileId)).toString(2)

    fun importAppearance(input: String): Boolean = runCatching {
        if (input.length > 200_000) return false
        val root = JSONObject(input)
        if (root.optString("format") != "amber-theme" || root.optInt("version") != 1) return false
        val appearance = root.optJSONObject("appearance") ?: return false
        importAppearanceForProfile(activeProfileId, appearance)
        true
    }.getOrDefault(false)

    fun importAppearanceForProfile(id: String, value: JSONObject) {
        if (!ProfileRules.validIdentifier(id)) return
        val style = value.optString("style").takeIf { it in setOf("amoled", "immersive", "cards", "light") } ?: "immersive"
        val palette = dev.liox.ambermusic.ui.AmberPalettes.fromId(value.optString("palette")).id
        val accent = ProfileRules.hexColorOrNull(value.optString("accent")).orEmpty()
        val corners = if (value.optString("corners") == "square") "square" else "rounded"
        val opacity = value.optDouble("opacity", .65).coerceIn(.1, 1.0).toFloat()
        val font = if (value.optString("font") == "expressive") "expressive" else "system"
        val icons = if (value.optString("icons") == "outlined") "outlined" else "filled"
        prefs.edit().putString(profileKey("visual_style", id), style)
            .putString(profileKey("theme", id), palette)
            .putString(profileKey("accent_hex", id), accent)
            .putString(profileKey("corners", id), corners)
            .putFloat(profileKey("wallpaper_opacity", id), opacity)
            .putString(profileKey("font_choice", id), font)
            .putString(profileKey("icon_style", id), icons)
            .putString(profileKey("custom_theme_name", id), ProfileRules.safeName(value.optString("name"))).apply()
        if (activeProfileId == id) activateProfile(id)
    }
}
