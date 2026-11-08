package dev.liox.ambermusic.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Small settings store: no network, no polling and no database migration. */
class AmberSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("amber_settings", Context.MODE_PRIVATE)
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
        prefs.edit().putString("theme", safe).apply()
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
        prefs.edit().putBoolean("immersive", enabled).apply()
    }

    fun setOffload(enabled: Boolean) {
        _offload.value = enabled
        prefs.edit().putBoolean("offload", enabled).apply()
    }
}
