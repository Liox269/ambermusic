package dev.liox.ambermusic.data.scanner

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** Music/ and its descendants are always indexed. Extra folders can be selected by the user. */
class MusicFolderSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "amber_music_folders", Context.MODE_PRIVATE
    )
    private val key = "extra_folder_paths"
    private val musicRoot = "music/"
    private val state = MutableStateFlow(load())

    val selectedFolders: StateFlow<Set<String>> = state

    @Synchronized
    fun saveSelection(selected: Set<String>) {
        val extras = selected.mapNotNull(::normalizedPath)
            .filterNot { it.equals(musicRoot, ignoreCase = true) }
            .toSet()
        check(preferences.edit().putStringSet(key, extras).commit()) {
            "No fue posible guardar las carpetas elegidas"
        }
        state.value = setOf("Music/") + extras
    }

    fun isIncluded(path: String, folders: Set<String> = state.value): Boolean {
        val candidate = normalizedPath(path)?.lowercase(Locale.ROOT) ?: return false
        return folders.any { folder ->
            val allowed = normalizedPath(folder)?.lowercase(Locale.ROOT)
            allowed != null && candidate.startsWith(allowed)
        }
    }

    private fun load(): Set<String> = setOf("Music/") +
        preferences.getStringSet(key, emptySet()).orEmpty().mapNotNull(::normalizedPath)

    companion object {
        fun normalizedPath(path: String): String? {
            val pieces = path.replace('\\', '/').trim().trim('/').split('/')
                .map { it.trim() }.filter { it.isNotEmpty() }
            if (pieces.isEmpty() || pieces.any { it == "." || it == ".." }) return null
            return pieces.joinToString("/", postfix = "/")
        }
    }
}
