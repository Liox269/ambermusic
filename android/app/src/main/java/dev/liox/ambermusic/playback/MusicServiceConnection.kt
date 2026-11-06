package dev.liox.ambermusic.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.withContext
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.liox.ambermusic.data.local.ArtistEntity
import dev.liox.ambermusic.data.local.MusicDao
import dev.liox.ambermusic.data.local.SongEntity
import dev.liox.ambermusic.domain.repository.MusicTrack
import dev.liox.ambermusic.domain.usecase.AudioQualityLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MusicServiceConnection(
    context: Context,
    private val dao: MusicDao,
    private val routeTracker: AudioRouteTracker
) {
    private val appContext = context.applicationContext
    private val saved = appContext.getSharedPreferences("amber_playback_resume", Context.MODE_PRIVATE)
    private var lastSnapshotMs = 0L
    private var sleepJob: Job? = null
    private val _sleepMinutes = MutableStateFlow(0)
    val sleepMinutes: StateFlow<Int> = _sleepMinutes
    private val _queueIds = MutableStateFlow<List<Long>>(emptyList())
    val queueIds: StateFlow<List<Long>> = _queueIds
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connectMutex = Mutex()
    private var controller: MediaController? = null
    private var ticker: Job? = null
    private val _songId = MutableStateFlow<Long?>(null)
    private val _artwork = MutableStateFlow<String?>(null)
    private val _playbackState = MutableStateFlow(PlaybackState())
    private val _progressState = MutableStateFlow(ProgressState())
    private val _audioSessionId = MutableStateFlow(-1)
    private val _error = MutableStateFlow<String?>(null)

    val playbackState: StateFlow<PlaybackState> = _playbackState
    val progressState: StateFlow<ProgressState> = _progressState
    val currentAudioRoute: StateFlow<AudioRoute> = routeTracker.route
    val audioSessionId: StateFlow<Int> = _audioSessionId
    val error: StateFlow<String?> = _error

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentSong: StateFlow<PlaybackSong?> = combine(
        _songId.flatMapLatest { id ->
            if (id == null) flowOf(null) else combine(
                dao.observeSongWithArtists(id),
                dao.observeOrderedSongArtists(id)
            ) { row, ordered -> row?.copy(artists = ordered) }
        }, _artwork
    ) { row, image ->
        row?.let {
            val s = it.song
            val track = MusicTrack(
                s.id, s.contentUri, s.title, s.artistDisplay, s.albumTitle,
                s.durationMs, s.format, s.bitrateBps, s.bitrateSource,
                s.sampleRateHz, s.bitDepth
            )
            PlaybackSong(s, it.artists, image, AudioQualityLabel.forTrack(track))
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish(player)
            snapshotQueue(player)
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                mediaItem?.mediaId?.toLongOrNull()?.let { id ->
                    scope.launch(Dispatchers.IO) { dao.recordPlay(id) }
                }
            }
        }
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _error.value = error.localizedMessage ?: "No se pudo reproducir el archivo"
        }
    }

    private val sessionListener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
            _audioSessionId.value = extras.getInt(MusicPlaybackService.EXTRA_AUDIO_SESSION_ID, -1)
        }
        override fun onDisconnected(controller: MediaController) {
            if (this@MusicServiceConnection.controller === controller) {
                ticker?.cancel()
                this@MusicServiceConnection.controller = null
                _playbackState.value = PlaybackState()
            }
        }
    }

    suspend fun connect(): MediaController {
        controller?.let { if (it.isConnected) return it }
        return connectMutex.withLock {
            controller?.let { if (it.isConnected) return@withLock it }
            val token = SessionToken(appContext, ComponentName(appContext, MusicPlaybackService::class.java))
            val future = MediaController.Builder(appContext, token)
                .setListener(sessionListener)
                .buildAsync()
            val attached = suspendCancellableCoroutine<MediaController> { continuation ->
                future.addListener({
                    try {
                        val result = future.get()
                        if (continuation.isActive) continuation.resume(result)
                        else MediaController.releaseFuture(future)
                    } catch (failure: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(failure)
                    }
                }, appContext.mainExecutor)
            }
            controller = attached
            attached.addListener(playerListener)
            _audioSessionId.value = attached.sessionExtras.getInt(
                MusicPlaybackService.EXTRA_AUDIO_SESSION_ID, -1
            )
            restoreIfEmpty(attached)
            publish(attached)
            attached
        }
    }

    private fun publish(player: Player) {
        val id = player.currentMediaItem?.mediaId?.toLongOrNull()
        _songId.value = id
        _queueIds.value = (0 until player.mediaItemCount).mapNotNull { index ->
            player.getMediaItemAt(index).mediaId.toLongOrNull()
        }
        _artwork.value = player.currentMediaItem?.mediaMetadata?.artworkUri?.toString()
        _playbackState.value = PlaybackState(
            isPlaying = player.isPlaying,
            shuffleModeEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            isReady = player.playbackState == Player.STATE_READY
        )
        updateProgress(player)
        if (player.isPlaying) startTicker() else {
            ticker?.cancel()
            ticker = null
        }
        routeTracker.refresh()
    }

    private fun updateProgress(player: Player) {
        _progressState.value = ProgressState(
            positionMs = player.currentPosition.coerceAtLeast(0),
            durationMs = player.duration.takeIf { it > 0 } ?: 0L
        )
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (true) {
                controller?.let {
                    updateProgress(it)
                    val currentTime = android.os.SystemClock.elapsedRealtime()
                    if (currentTime - lastSnapshotMs > 5_000L) {
                        snapshotQueue(it)
                    }
                }
                delay(250)
            }
        }
    }

    suspend fun playQueue(songs: List<SongEntity>, initialIndex: Int = 0) {
        if (songs.isEmpty()) return
        require(initialIndex in songs.indices)
        val target = connect()
        val items = songs.map { song ->
            val metadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artistDisplay)
                .setAlbumTitle(song.albumTitle)
                .setDurationMs(song.durationMs)
                .build()
            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(Uri.parse(song.contentUri))
                .setMediaMetadata(metadata)
                .build()
        }
        _error.value = null
        target.setMediaItems(items, initialIndex, 0L)
        target.prepare()
        target.play()
        snapshotQueue(target, force = true)
    }

    /** Restore previous queue paused: never start playing unexpectedly after app relaunch. */
    private suspend fun restoreIfEmpty(target: MediaController) {
        if (target.mediaItemCount != 0) return
        val text = withContext(Dispatchers.IO) { saved.getString("queue", null) } ?: return
        try {
            val data = JSONObject(text)
            val ids = data.getJSONArray("ids")
            val songs = withContext(Dispatchers.IO) {
                (0 until ids.length()).mapNotNull { index -> dao.getSong(ids.getLong(index)) }
            }.filter { it.isVisible && it.durationMs > 60_000 }
            if (songs.isEmpty()) return
            val savedCurrentId = data.optString("currentId", "")
            val index = songs.indexOfFirst { it.id.toString() == savedCurrentId }
                .takeIf { it >= 0 }
                ?: data.optInt("index", 0).coerceIn(0, songs.lastIndex)
            target.setMediaItems(songs.map { song ->
                MediaItem.Builder().setMediaId(song.id.toString())
                    .setUri(Uri.parse(song.contentUri))
                    .setMediaMetadata(MediaMetadata.Builder()
                        .setTitle(song.title).setArtist(song.artistDisplay)
                        .setAlbumTitle(song.albumTitle).setDurationMs(song.durationMs).build())
                    .build()
            }, index, data.optLong("position", 0L).coerceAtLeast(0L))
            target.repeatMode = data.optInt("repeat", Player.REPEAT_MODE_OFF)
            target.shuffleModeEnabled = data.optBoolean("shuffle", false)
            target.prepare()
            target.pause()
        } catch (failure: Exception) {
            _error.value = failure.localizedMessage
                ?: "No se pudo restaurar la sesión de reproducción"
        }
    }

    private fun snapshotQueue(player: Player, force: Boolean = false) {
        if (player.mediaItemCount == 0 || player.currentMediaItemIndex < 0) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && now - lastSnapshotMs < 4_500L) return
        lastSnapshotMs = now
        val data = JSONObject().put("ids", JSONArray().apply {
            for (index in 0 until player.mediaItemCount) {
                player.getMediaItemAt(index).mediaId.toLongOrNull()?.let(::put)
            }
        }).put("index", player.currentMediaItemIndex)
            .put("currentId", player.currentMediaItem?.mediaId)
            .put("position", player.currentPosition.coerceAtLeast(0L))
            .put("shuffle", player.shuffleModeEnabled)
            .put("repeat", player.repeatMode)
        scope.launch(Dispatchers.IO) { saved.edit().putString("queue", data.toString()).apply() }
    }

    fun saveSession() { controller?.let { snapshotQueue(it, force = true) } }

    fun moveQueueItem(from: Int, to: Int) = scope.launch {
        val player = connect()
        if (from in 0 until player.mediaItemCount && to in 0 until player.mediaItemCount && from != to) {
            player.moveMediaItem(from, to)
            snapshotQueue(player, force = true)
        }
    }

    fun playNext(song: SongEntity) = scope.launch {
        val player = connect()
        val item = MediaItem.Builder().setMediaId(song.id.toString())
            .setUri(Uri.parse(song.contentUri))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(song.title)
                .setArtist(song.artistDisplay).setAlbumTitle(song.albumTitle).build()).build()
        player.addMediaItem((player.currentMediaItemIndex + 1).coerceAtLeast(0), item)
        snapshotQueue(player, force = true)
    }

    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        val safe = minutes.coerceIn(0, 180)
        _sleepMinutes.value = safe
        if (safe == 0) return
        sleepJob = scope.launch {
            delay(safe * 60_000L)
            _sleepMinutes.value = 0
            stopAndExit {}
        }
    }

    fun togglePlayPause() = scope.launch {
        val p = connect()
        if (p.isPlaying) p.pause() else {
            if (p.playbackState == Player.STATE_IDLE) p.prepare()
            p.play()
        }
    }
    fun seekTo(positionMs: Long) = scope.launch { connect().seekTo(positionMs.coerceAtLeast(0)) }
    fun next() = scope.launch { connect().seekToNextMediaItem() }
    fun previous() = scope.launch {
        val p = connect()
        if (p.currentPosition > 3_000) p.seekTo(0) else p.seekToPreviousMediaItem()
    }
    fun toggleShuffle() = scope.launch {
        val p = connect()
        p.shuffleModeEnabled = !p.shuffleModeEnabled
    }
    fun toggleRepeat() = scope.launch {
        val p = connect()
        p.repeatMode = when (p.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
    /** Explicit exit: stop the queue, detach our controller and terminate the service. */
    fun stopAndExit(onStopped: () -> Unit) = scope.launch {
        try {
            controller?.let { attached ->
                if (attached.isConnected) {
                    attached.pause()
                    attached.stop()
                    attached.clearMediaItems()
                    publish(attached)
                }
                attached.removeListener(playerListener)
                attached.release()
            }
            controller = null
            ticker?.cancel()
            ticker = null
            _songId.value = null
            _artwork.value = null
            _playbackState.value = PlaybackState()
            _progressState.value = ProgressState()
            appContext.stopService(Intent(appContext, MusicPlaybackService::class.java))
        } finally {
            onStopped()
        }
    }

    fun close() {
        sleepJob?.cancel()
        ticker?.cancel()
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        routeTracker.close()
        scope.cancel()
    }
}

data class PlaybackSong(
    val song: SongEntity,
    val artists: List<ArtistEntity>,
    val artworkUri: String?,
    val qualityLabel: String
)

data class PlaybackState(
    val isPlaying: Boolean = false,
    val shuffleModeEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val isReady: Boolean = false
)

data class ProgressState(val positionMs: Long = 0L, val durationMs: Long = 0L)
