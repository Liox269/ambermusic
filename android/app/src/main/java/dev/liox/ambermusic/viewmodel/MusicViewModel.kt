package dev.liox.ambermusic.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.liox.ambermusic.data.local.ArtistEntity
import dev.liox.ambermusic.data.local.CategoryEntity
import dev.liox.ambermusic.data.local.MusicDao
import dev.liox.ambermusic.data.local.SongCategoryCrossRef
import dev.liox.ambermusic.data.local.SongEntity
import dev.liox.ambermusic.smart.SmartPlaylistStore
import dev.liox.ambermusic.smart.SmartPlaylist
import dev.liox.ambermusic.smart.SmartRules
import dev.liox.ambermusic.duplicates.DuplicateFinder
import dev.liox.ambermusic.lyrics.LyricsRepository
import dev.liox.ambermusic.lyrics.LyricLine
import dev.liox.ambermusic.backup.LibraryBackup
import dev.liox.ambermusic.settings.AmberSettings
import dev.liox.ambermusic.profiles.ProfileStore
import dev.liox.ambermusic.artwork.ArtworkRepository
import android.net.Uri
import dev.liox.ambermusic.playlists.M3uCodec
import dev.liox.ambermusic.playlists.LocalTrackPath
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import dev.liox.ambermusic.data.scanner.LocalMusicScanner
import dev.liox.ambermusic.data.scanner.MusicFolderSettings
import dev.liox.ambermusic.playback.MusicServiceConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicViewModel(
    private val dao: MusicDao,
    private val scanner: LocalMusicScanner,
    private val playbackConnection: MusicServiceConnection,
    private val folderSettings: MusicFolderSettings,
    private val settings: AmberSettings,
    private val lyrics: LyricsRepository,
    private val backup: LibraryBackup,
    private val artwork: ArtworkRepository,
    private val smartStore: SmartPlaylistStore,
    private val duplicateFinder: DuplicateFinder,
    private val profiles: ProfileStore
) : ViewModel() {
    val smartPlaylists = smartStore.rules
    val hiddenDuplicates: StateFlow<Set<String>> = settings.hiddenDuplicateUris

    val replayGain = settings.replayGain
    val smoothTransition = settings.smoothTransition
    val crossfadeSeconds = settings.crossfadeSeconds
    val performanceProfile = settings.performanceProfile
    val restoreSession = settings.restoreSession
    val previousBehavior = settings.previousBehavior
    fun setRestoreSession(value: Boolean) = settings.setRestoreSession(value)
    fun setPreviousBehavior(value: String) = settings.setPreviousBehavior(value)
    fun setCrossfadeSeconds(value: Int) = settings.setCrossfadeSeconds(value)
    fun setPerformanceProfile(value: String) = settings.setPerformanceProfile(value)
    val immersive = settings.immersive
    private val _duplicateGroups = MutableStateFlow<List<List<SongEntity>>>(emptyList())
    val duplicateGroups: StateFlow<List<List<SongEntity>>> = _duplicateGroups
    private val _duplicateProgress = MutableStateFlow("")
    val duplicateProgress: StateFlow<String> = _duplicateProgress
    private val _duplicatesRunning = MutableStateFlow(false)
    val duplicatesRunning: StateFlow<Boolean> = _duplicatesRunning
    private val scanRunning = MutableStateFlow(false)
    private val scanMessage = MutableStateFlow<String?>(null)
    private val scanned = MutableStateFlow(false)
    private val sourceName = MutableStateFlow("Canciones")
    val selectedFolders: StateFlow<Set<String>> = folderSettings.selectedFolders
    private val folderList = MutableStateFlow<List<String>>(emptyList())
    val availableFolders: StateFlow<List<String>> = folderList
    private val folderLoadingState = MutableStateFlow(false)
    val folderLoading: StateFlow<Boolean> = folderLoadingState

    private val libraryData = combine(
        dao.observeSongs(), dao.observeCategories(), dao.observeArtistsWithSongs(), dao.observeAlbums(),
        dao.observePlaylists(), dao.observeMostPlayedSongs(), dao.observeRecentlyPlayedSongs(),
        dao.observeFavoriteSongs(),
        dao.observeTopSince(System.currentTimeMillis() - 7L * 86_400_000L),
        dao.observeTopSince(System.currentTimeMillis() - 30L * 86_400_000L),
        dao.observePlayCountSince(System.currentTimeMillis() - 7L * 86_400_000L),
        dao.observePlayCountSince(System.currentTimeMillis() - 30L * 86_400_000L)
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        LibraryUiState(
            songs = values[0] as List<SongEntity>,
            categories = values[1] as List<dev.liox.ambermusic.data.local.CategoryEntity>,
            artists = (values[2] as List<dev.liox.ambermusic.data.local.ArtistWithSongs>).map { it.artist },
            albums = values[3] as List<dev.liox.ambermusic.data.local.AlbumSummary>,
            playlists = values[4] as List<dev.liox.ambermusic.data.local.PlaylistEntity>,
            mostPlayed = values[5] as List<SongEntity>,
            recentlyPlayed = values[6] as List<SongEntity>,
            favoriteSongs = values[7] as List<SongEntity>,
            weeklyTop = values[8] as List<SongEntity>,
            monthlyTop = values[9] as List<SongEntity>,
            weekPlays = values[10] as Long,
            monthPlays = values[11] as Long
        )
    }

    private val baseLibraryUiState = combine(
        libraryData, scanRunning, scanMessage, scanned, hiddenDuplicates
    ) { base, running, message, hasScanned, hidden ->
        val songs = base.songs.filterNot { it.contentUri in hidden }
        val countsByAlbum = songs.groupingBy { it.albumKey }.eachCount()
        base.copy(
            songs = songs,
            favoriteSongs = base.favoriteSongs.filterNot { it.contentUri in hidden },
            mostPlayed = base.mostPlayed.filterNot { it.contentUri in hidden },
            recentlyPlayed = base.recentlyPlayed.filterNot { it.contentUri in hidden },
            weeklyTop = base.weeklyTop.filterNot { it.contentUri in hidden },
            monthlyTop = base.monthlyTop.filterNot { it.contentUri in hidden },
            albums = base.albums.mapNotNull { album ->
                val count = countsByAlbum[album.albumKey] ?: 0
                if (count == 0) null else album.copy(songCount = count.toLong())
            },
            scanning = running, message = message, scanned = hasScanned
        )
    }

    val libraryUiState: StateFlow<LibraryUiState> = combine(
        baseLibraryUiState, profiles.active, profiles.favoriteUris
    ) { state, profile, favoriteUris ->
        if (profile.id == "default") state else state.copy(
            favoriteSongs = state.songs.filter { it.contentUri in favoriteUris }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    val theme = settings.theme
    val visualStyle = settings.visualStyle
    val density = settings.density
    val reduceMotion = settings.reduceMotion
    val fitCover = settings.fitCover
    val currentRoute = playbackConnection.currentAudioRoute
    val autoOutputProfiles = settings.autoOutputProfiles
    val routePresets = settings.routePresets
    fun setAutoOutputProfiles(value: Boolean) = settings.setAutoOutputProfiles(value)
    fun setRouteReplayGain(name: String, mode: String) = settings.setRouteReplayGain(name, mode)
    fun setVisualStyle(value: String) = settings.setVisualStyle(value)
    fun setDensity(value: String) = settings.setDensity(value)
    fun setReduceMotion(value: Boolean) = settings.setReduceMotion(value)
    fun setFitCover(value: Boolean) = settings.setFitCover(value)
    val offload = settings.offload
    val queueIds = playbackConnection.queueIds
    val audioSessionId = playbackConnection.audioSessionId
    val sleepMinutes = playbackConnection.sleepMinutes
    private val lyricsRevision = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentLyrics: StateFlow<List<LyricLine>> = combine(
        playbackConnection.currentSong, lyricsRevision
    ) { playing, revision -> playing?.song?.id }
        .mapLatest { id -> if (id == null) emptyList() else lyrics.loadAdjusted(id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val playerBase = combine(
        playbackConnection.currentSong,
        playbackConnection.playbackState,
        playbackConnection.progressState,
        playbackConnection.currentAudioRoute,
        sourceName
    ) { playing, state, progress, route, source ->
        PlayerUiState(
            song = playing?.song,
            artists = playing?.artists ?: emptyList(),
            artistInitials = playing?.artists?.map { initialsOf(it.name) } ?: emptyList(),
            qualityLabel = playing?.qualityLabel ?: "",
            artworkUri = playing?.artworkUri,
            sourceName = source,
            route = route,
            playback = state,
            progress = progress
        )
    }

    val playerUiState: StateFlow<PlayerUiState> = combine(
        playerBase, dao.observeFavoriteIds(), profiles.active, profiles.favoriteUris
    ) { base, favorites, profile, profileFavorites ->
        base.copy(isFavorite = if (profile.id == "default") base.song?.id in favorites
            else base.song?.contentUri in profileFavorites)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerUiState())

    val error: StateFlow<String?> = playbackConnection.error

    init {
        viewModelScope.launch {
            try { playbackConnection.connect() }
            catch (failure: Exception) { scanMessage.value = "Servicio de audio: ${failure.localizedMessage}" }
        }
    }

    fun scanLocalMusic() {
        if (scanRunning.value) return
        viewModelScope.launch {
            scanRunning.value = true
            scanMessage.value = null
            try {
                val result = withContext(Dispatchers.IO) { scanner.scan() }
                scanned.value = true
                scanMessage.value = "Actualizadas ${result.addedOrUpdated}, sin cambios ${result.unchanged}, errores ${result.errors}."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                scanMessage.value = failure.localizedMessage ?: "Error leyendo música local"
            } finally {
                scanRunning.value = false
            }
        }
    }

    fun loadAvailableFolders() {
        viewModelScope.launch {
            folderLoadingState.value = true
            try {
                folderList.value = withContext(Dispatchers.IO) { scanner.listAvailableFolders() }
            } catch (failure: Exception) {
                scanMessage.value = failure.localizedMessage ?: "No se pudieron consultar las carpetas"
            } finally {
                folderLoadingState.value = false
            }
        }
    }

    fun updateFolderSelection(folders: Set<String>) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { folderSettings.saveSelection(folders) }
                // Force a follow-up scan even if a previous scan is still running.
                scanRunning.value = true
                val result = withContext(Dispatchers.IO) { scanner.scan() }
                scanned.value = true
                scanMessage.value = "Biblioteca actualizada: ${result.addedOrUpdated} cambios, ${result.removed} ocultas."
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                scanMessage.value = failure.localizedMessage ?: "No se pudo actualizar las carpetas"
            } finally {
                scanRunning.value = false
            }
        }
    }

    fun playSelection(songs: List<SongEntity>, songId: Long) = viewModelScope.launch {
        play(songs.filter { it.isVisible && it.durationMs > LocalMusicScanner.MIN_SONG_DURATION_MS },
            songId, "Canciones")
    }

    fun playAll(songId: Long) = viewModelScope.launch {
        val songs = withContext(Dispatchers.IO) { dao.observeSongs().first() }
        play(songs, songId, "Canciones")
    }

    fun playFromCategory(categoryId: Long, songId: Long? = null) = viewModelScope.launch {
        val data = withContext(Dispatchers.IO) {
            dao.observeCategoryWithSongs(categoryId).first()
        } ?: return@launch
        play(data.songs.filter { it.isVisible && it.durationMs > LocalMusicScanner.MIN_SONG_DURATION_MS }.sortedBy { it.title.lowercase() }, songId, data.category.name)
    }

    fun playFromArtist(artistId: Long, songId: Long? = null) = viewModelScope.launch {
        val data = withContext(Dispatchers.IO) {
            dao.observeArtistWithSongs(artistId).first()
        } ?: return@launch
        play(data.songs.filter { it.isVisible && it.durationMs > LocalMusicScanner.MIN_SONG_DURATION_MS }.sortedBy { it.title.lowercase() }, songId, data.artist.name)
    }

    fun playFromAlbum(albumKey: String, songId: Long? = null) = viewModelScope.launch {
        val songs = withContext(Dispatchers.IO) { dao.observeAlbumSongs(albumKey).first() }
        play(songs, songId, songs.firstOrNull()?.albumTitle ?: "Álbum")
    }

    private suspend fun play(songs: List<SongEntity>, songId: Long?, name: String) {
        if (songs.isEmpty()) return
        val available = songs.filterNot { it.contentUri in hiddenDuplicates.value }
        if (available.isEmpty()) return
        val index = available.indexOfFirst { it.id == songId }.coerceAtLeast(0)
        sourceName.value = name
        try {
            playbackConnection.playQueue(available, index)
            withContext(Dispatchers.IO) { dao.recordPlay(available[index].id) }
        } catch (failure: Exception) {
            scanMessage.value = failure.localizedMessage ?: "No se pudo reproducir"
        }
    }

    fun savePlaybackSession() = playbackConnection.saveSession()

    fun stopAndExit(onStopped: () -> Unit) { playbackConnection.stopAndExit(onStopped) }

    fun togglePlayPause() { playbackConnection.togglePlayPause() }
    fun seekTo(positionMs: Long) { playbackConnection.seekTo(positionMs) }
    fun next() { playbackConnection.next() }
    fun previous() { playbackConnection.previous() }
    fun toggleShuffle() { playbackConnection.toggleShuffle() }
    fun toggleRepeat() { playbackConnection.toggleRepeat() }

    fun addManyToPlaylists(songIds: Set<Long>, playlistIds: Set<Long>) =
        viewModelScope.launch(Dispatchers.IO) {
            for (playlistId in playlistIds) {
                for (songId in songIds) {
                    if (dao.getSong(songId)?.isVisible == true) {
                        dao.appendSongToPlaylist(playlistId, songId)
                    }
                }
            }
        }

    fun favoriteMany(songIds: Set<Long>) = viewModelScope.launch(Dispatchers.IO) {
        if (profiles.active.value.id == "default") {
            songIds.forEach { id -> if (dao.getSong(id)?.isVisible == true) dao.setFavorite(id, true) }
        } else profiles.addFavorites(songIds.mapNotNull { dao.getSong(it)?.takeIf { song -> song.isVisible }?.contentUri }.toSet())
    }

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            if (profiles.active.value.id == "default") {
                val favorite = dao.getUserState(songId)?.isFavorite ?: false
                dao.setFavorite(songId, !favorite)
            } else dao.getSong(songId)?.let { profiles.toggleFavorite(it.contentUri) }
        }
    }

    fun toggleFavorite() {
        val current = playerUiState.value.song ?: return
        viewModelScope.launch(Dispatchers.IO) {
            if (profiles.active.value.id == "default") {
                val favorite = dao.getUserState(current.id)?.isFavorite ?: false
                dao.setFavorite(current.id, !favorite)
            } else profiles.toggleFavorite(current.contentUri)
        }
    }

    fun createCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try { dao.createCategory(name) }
            catch (failure: Exception) { scanMessage.value = failure.localizedMessage }
        }
    }

    fun addToCategory(songId: Long, categoryId: Long) = viewModelScope.launch(Dispatchers.IO) {
        dao.insertSongCategory(SongCategoryCrossRef(songId, categoryId))
    }

    fun removeFromCategory(songId: Long, categoryId: Long) = viewModelScope.launch(Dispatchers.IO) {
        dao.removeSongFromCategory(songId, categoryId)
    }

    fun deleteCategory(categoryId: Long) = viewModelScope.launch(Dispatchers.IO) {
        dao.deleteCategory(categoryId)
    }

    fun observeCategorySongs(categoryId: Long) = dao.observeSongsInCategory(categoryId)

    fun observeArtistSongs(artistId: Long) = dao.observeArtistWithSongs(artistId)
    fun observeAlbumSongs(albumKey: String) = dao.observeAlbumSongs(albumKey)
    fun setTheme(value: String) = settings.setTheme(value)
    fun setReplayGain(value: String) = settings.setReplayGain(value)
    fun setSmoothTransitions(enabled: Boolean) = settings.setSmoothTransition(enabled)
    fun setImmersive(enabled: Boolean) = settings.setImmersive(enabled)

    fun createSmartPlaylist(name: String, type: String, value: String) = smartStore.add(name, type, value)
    fun removeSmartPlaylist(id: String) = smartStore.remove(id)

    fun smartSongs(rule: SmartPlaylist, library: LibraryUiState): List<SongEntity> {
        val states = userStates.value.associateBy { it.songId }
        return library.songs.filter { SmartRules.matches(rule, it, states[it.id]) }
    }

    fun playFromSmartList(rule: SmartPlaylist, library: LibraryUiState, songId: Long) = viewModelScope.launch {
        play(smartSongs(rule, library), songId, rule.name)
    }

    /** Hide only members of a SHA-256-confirmed group, keeping the user's chosen file. */
    fun keepDuplicateCopy(group: List<SongEntity>, keepId: Long) {
        if (group.size < 2 || group.none { it.id == keepId }) return
        val previous = hiddenDuplicates.value
        val groupUris = group.map { it.contentUri }.toSet()
        val chosenUri = group.first { it.id == keepId }.contentUri
        settings.setHiddenDuplicateUris(
            (previous - groupUris) + (groupUris - chosenUri)
        )
        _duplicateProgress.value = "Copia conservada. Las demás están ocultas (no eliminadas)."
    }

    /** Process the already analyzed exact groups; favorites and Music/ take priority. */
    fun hideExtraDuplicateCopies() {
        val favorites = libraryUiState.value.favoriteSongs.map { it.id }.toSet()
        var hidden = hiddenDuplicates.value
        for (group in duplicateGroups.value) {
            val keep = group.sortedWith(
                compareByDescending<SongEntity> { it.id in favorites }
                    .thenByDescending { it.folderPath.startsWith("Music/", ignoreCase = true) }
                    .thenBy { it.id }
            ).firstOrNull() ?: continue
            val groupUris = group.map { it.contentUri }.toSet()
            hidden = (hidden - groupUris) + (groupUris - keep.contentUri)
        }
        settings.setHiddenDuplicateUris(hidden)
        _duplicateProgress.value = "Ocultadas ${hidden.size} copias. No se borró ningún archivo."
    }

    fun showAllHiddenDuplicateCopies() {
        settings.setHiddenDuplicateUris(emptySet())
        _duplicateProgress.value = "Todas las copias vuelven a estar visibles."
    }

    fun analyzeDuplicates(songs: List<SongEntity>) {
        if (_duplicatesRunning.value) return
        viewModelScope.launch {
            _duplicatesRunning.value = true
            _duplicateProgress.value = "Preparando análisis…"
            try {
                _duplicateGroups.value = duplicateFinder.analyze(dao.observeSongs().first()) { done, total ->
                    _duplicateProgress.value = "$done / $total archivos comprobados"
                }
                _duplicateProgress.value = "${_duplicateGroups.value.size} grupos idénticos detectados"
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                _duplicateProgress.value = failure.localizedMessage ?: "No se pudo analizar"
            } finally { _duplicatesRunning.value = false }
        }
    }

    private val userStates = dao.observeUserStates().stateIn(viewModelScope,
        SharingStarted.WhileSubscribed(5_000), emptyList())
    fun setOffload(enabled: Boolean) = settings.setOffload(enabled)
    fun setSleepTimer(minutes: Int) = playbackConnection.setSleepTimer(minutes)
    fun moveQueueItem(from: Int, to: Int) = playbackConnection.moveQueueItem(from, to)
    fun playNext(song: SongEntity) = playbackConnection.playNext(song)

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            try { dao.createPlaylist(name) }
            catch (failure: Exception) { scanMessage.value = failure.localizedMessage }
        }
    }
    fun addToPlaylist(songId: Long, playlistId: Long) = viewModelScope.launch(Dispatchers.IO) {
        dao.appendSongToPlaylist(playlistId, songId)
    }
    fun removeFromPlaylist(songId: Long, playlistId: Long) = viewModelScope.launch(Dispatchers.IO) {
        dao.removeSongFromPlaylist(songId, playlistId)
    }
    fun deletePlaylist(playlistId: Long) = viewModelScope.launch(Dispatchers.IO) { dao.deletePlaylist(playlistId) }
    fun observePlaylistSongs(playlistId: Long) = dao.observePlaylistSongsOrdered(playlistId)
    fun playFromPlaylist(playlistId: Long, songId: Long? = null) = viewModelScope.launch {
        val list = withContext(Dispatchers.IO) { dao.observePlaylistSongsOrdered(playlistId).first() }
        val name = withContext(Dispatchers.IO) { dao.observePlaylists().first().firstOrNull { it.id == playlistId }?.name } ?: "Playlist"
        play(list, songId, name)
    }

    /** SAF handles reading and writing. The VM processes text off the UI thread. */
    fun importM3uText(text: String, onDone: (String) -> Unit) = viewModelScope.launch {
        try {
            val result = withContext(Dispatchers.IO) {
                val entries = M3uCodec.parse(text)
                val library = dao.observeSongs().first()
                val tracks = library.map { LocalTrackPath(it.id, it.folderPath, it.displayName, it.title, it.durationMs) }
                val matched = M3uCodec.match(entries, tracks)
                val name = "Importada ${System.currentTimeMillis()}"
                val playlistId = dao.createPlaylist(name)
                for (id in matched.matchedIds) dao.appendSongToPlaylist(playlistId, id)
                matched
            }
            onDone("Importadas ${result.matchedIds.size} canciones; " +
                "faltan ${result.missing.size}, ambiguas ${result.ambiguous.size}.")
        } catch (error: Exception) {
            onDone("Error M3U8: ${error.localizedMessage ?: "archivo no válido"}")
        }
    }

    suspend fun exportPlaylistM3u(playlistId: Long): String = withContext(Dispatchers.IO) {
        val songs = dao.observePlaylistSongsOrdered(playlistId).first()
        M3uCodec.export(songs.map {
            LocalTrackPath(it.id, it.folderPath, it.displayName, it.title, it.durationMs)
        })
    }

    fun saveCurrentQueueAsPlaylist(name: String, onDone: (String) -> Unit) = viewModelScope.launch {
        try {
            val ids = playbackConnection.queueIds.value
            val saved = withContext(Dispatchers.IO) {
                val playlistId = dao.createPlaylist(name)
                for (id in ids.distinct()) {
                    if (dao.getSong(id)?.isVisible == true) dao.appendSongToPlaylist(playlistId, id)
                }
                ids.size
            }
            onDone("Cola guardada: $saved canciones")
        } catch (error: Exception) {
            onDone(error.localizedMessage ?: "No se pudo guardar la cola")
        }
    }

    val namedQueues = playbackConnection.namedQueueNames
    fun saveNamedQueue(name: String) { playbackConnection.saveNamedQueue(name) }
    fun restoreNamedQueue(name: String) { playbackConnection.restoreNamedQueue(name) }
    fun deleteNamedQueue(name: String) = playbackConnection.deleteNamedQueue(name)

    fun fetchLyricsFromLrclib(onDone: (String) -> Unit) {
        val song = playerUiState.value.song ?: return
        viewModelScope.launch {
            val result = runCatching {
                lyrics.fetchFromLrclib(song.id, song.title, song.artistDisplay, song.albumTitle, song.durationMs)
            }
            result.onSuccess { found ->
                if (found) lyricsRevision.value += 1
                onDone(if (found) "Letras descargadas desde LRCLIB" else "No hay letras disponibles o ya tienes un archivo local")
            }.onFailure {
                onDone(it.localizedMessage ?: "No se pudo descargar la letra")
            }
        }
    }

    fun importLyrics(uri: Uri) {
        val id = playerUiState.value.song?.id ?: return
        viewModelScope.launch {
            try {
                lyrics.import(id, uri)
                lyricsRevision.value += 1
            } catch (failure: Exception) { scanMessage.value = "Letras: ${failure.localizedMessage}" }
        }
    }
    fun adjustLyricsOffset(deltaMs: Long) {
        val id = playerUiState.value.song?.id ?: return
        lyrics.adjustOffset(id, deltaMs)
        lyricsRevision.value += 1
    }

    fun saveEditedLyrics(text: String) {
        val id = playerUiState.value.song?.id ?: return
        viewModelScope.launch {
            try {
                lyrics.saveEdited(id, text)
                lyrics.adjustOffset(id, -lyrics.getOffsetMs(id))
                lyricsRevision.value += 1
            }
            catch (failure: Exception) { scanMessage.value = failure.localizedMessage ?: "Error al editar letras" }
        }
    }

    fun clearLyrics() {
        val id = playerUiState.value.song?.id ?: return
        viewModelScope.launch { lyrics.clear(id); lyricsRevision.value += 1 }
    }
    suspend fun exportBackup(): String = backup.export()
    suspend fun importBackup(json: String): Int = backup.restore(json)

    fun changeDisplayMetadata(songId: Long, title: String, artist: String, album: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try { dao.saveDisplayOverride(songId, title, artist, album) }
            catch (failure: Exception) { scanMessage.value = failure.localizedMessage }
        }
    }

    fun setCustomArtwork(uri: Uri) {
        val song = playerUiState.value.song ?: return
        viewModelScope.launch {
            try { artwork.setCustom(song.id, uri) }
            catch (failure: Exception) { scanMessage.value = failure.localizedMessage ?: "No se pudo cambiar la portada" }
        }
    }
    fun clearCustomArtwork() {
        val song = playerUiState.value.song ?: return
        viewModelScope.launch { artwork.clearCustom(song.id) }
    }

}
