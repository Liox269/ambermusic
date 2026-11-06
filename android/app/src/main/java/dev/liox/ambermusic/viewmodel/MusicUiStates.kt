package dev.liox.ambermusic.viewmodel

import androidx.compose.runtime.Immutable
import dev.liox.ambermusic.data.local.AlbumSummary
import dev.liox.ambermusic.data.local.ArtistEntity
import dev.liox.ambermusic.data.local.CategoryEntity
import dev.liox.ambermusic.data.local.SongEntity
import dev.liox.ambermusic.data.local.PlaylistEntity
import dev.liox.ambermusic.playback.AudioRoute
import dev.liox.ambermusic.playback.PlaybackState
import dev.liox.ambermusic.playback.ProgressState

@Immutable
data class PlayerUiState(
    val song: SongEntity? = null,
    val artists: List<ArtistEntity> = emptyList(),
    val artistInitials: List<String> = emptyList(),
    val qualityLabel: String = "",
    val artworkUri: String? = null,
    val sourceName: String = "Canciones",
    val isFavorite: Boolean = false,
    val route: AudioRoute = AudioRoute("Altavoz del teléfono", false),
    val playback: PlaybackState = PlaybackState(),
    val progress: ProgressState = ProgressState()
)

@Immutable
data class LibraryUiState(
    val songs: List<SongEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val artists: List<ArtistEntity> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val mostPlayed: List<SongEntity> = emptyList(),
    val recentlyPlayed: List<SongEntity> = emptyList(),
    val favoriteSongs: List<SongEntity> = emptyList(),
    val weeklyTop: List<SongEntity> = emptyList(),
    val monthlyTop: List<SongEntity> = emptyList(),
    val weekPlays: Long = 0,
    val monthPlays: Long = 0,
    val scanned: Boolean = false,
    val scanning: Boolean = false,
    val message: String? = null
)

fun initialsOf(name: String): String = name.trim().split(Regex("\\s+"))
    .filter { it.isNotEmpty() }
    .take(2)
    .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
    .joinToString("")
