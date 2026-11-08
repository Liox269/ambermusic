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
import dev.liox.ambermusic.settings.AmberSettings
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
    private val routeTracker: AudioRouteTracker,
    private val settings: AmberSettings
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
                s.sampleRateHz, s.bitDepth, s.codecMime, s.channelCount
            )
            PlaybackSong(s, it.artists, image, AudioQualityLabel.forTrack(track))
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish(player)
            val importantChange = events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) ||
                events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
                events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                events.contains(Player.EVENT_REPEAT_MODE_CHANGED) ||
                events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED)
            snapshotQueue(player, force = importantChange)
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
                    if (PlaybackRefreshPolicy.shouldPersist(lastSnapshotMs, currentTime)) {
                        snapshotQueue(it)
                    }
                }
                delay(PlaybackRefreshPolicy.PROGRESS_POLL_MS)
            }
        }
    }

    suspend fun playQueue(songs: List<SongEntity>, initialIndex: Int = 0) {
        if (songs.isEmpty()) return
        require(initialIndex in songs.indices)
        val target = connect()
        // New selection is an unnamed queue. Previously saved queue profiles remain intact.
        activeQueueName = null
        saved.edit().remove("activeQueueName").apply()
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

    /** On cold start, restore the last session. Never auto-play after relaunch. */
    private suspend fun restoreIfEmpty(target: MediaController) {
        if (!settings.restoreSession.value || target.mediaItemCount != 0) return
        val text = withContext(Dispatchers.IO) { saved.getString("queue", null) } ?: return
        if (restoreSnapshot(target, text)) {
            activeQueueName = saved.getString("activeQueueName", null)
        }
    }

    /** Reconstruct by stable song ID, not the old numeric index. Missing songs are skipped. */
    private suspend fun restoreSnapshot(target: MediaController, text: String): Boolean {
        return try {
            val data = JSONObject(text)
            val idsJson = data.getJSONArray("ids")
            val savedIds = (0 until idsJson.length()).map { idsJson.getLong(it) }
            val surviving = withContext(Dispatchers.IO) {
                savedIds.mapIndexedNotNull { originalIndex, id ->
                    dao.getSong(id)?.takeIf { it.isVisible && it.durationMs > 60_000L }
                        ?.let { originalIndex to it }
                }
            }
            if (surviving.isEmpty()) return false
            val songs = surviving.map { it.second }
            val savedIndex = data.optInt("index", 0)
            val savedId = if (data.has("currentId") && !data.isNull("currentId"))
                data.optLong("currentId") else null
            val index = QueueRestorePlanner.selectedIndex(
                savedIds, surviving.map { it.first }, savedIndex, savedId
            )
            if (index !in songs.indices) return false
            val sameSong = savedId == songs[index].id ||
                (savedId == null && savedIds.getOrNull(savedIndex) == songs[index].id)
            val position = if (sameSong)
                data.optLong("position", 0L).coerceIn(0L, songs[index].durationMs.coerceAtLeast(0L))
            else 0L
            target.setMediaItems(songs.map(::mediaItemFor), index, position)
            target.repeatMode = data.optInt("repeat", Player.REPEAT_MODE_OFF)
            target.shuffleModeEnabled = data.optBoolean("shuffle", false)
            target.prepare()
            target.pause()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun mediaItemFor(song: SongEntity): MediaItem = MediaItem.Builder()
        .setMediaId(song.id.toString())
        .setUri(Uri.parse(song.contentUri))
        .setMediaMetadata(MediaMetadata.Builder()
            .setTitle(song.title).setArtist(song.artistDisplay)
            .setAlbumTitle(song.albumTitle).setDurationMs(song.durationMs).build())
        .build()

    private var activeQueueName: String? = saved.getString("activeQueueName", null)
    private val _namedQueueNames = MutableStateFlow(readQueueNames())
    val namedQueueNames: StateFlow<List<String>> = _namedQueueNames

    private fun readQueueNames(): List<String> = runCatching {
        val data = JSONObject(saved.getString("namedQueues", "{}") ?: "{}")
        data.keys().asSequence().toList().sorted()
    }.getOrDefault(emptyList())

    /** Captures the active queue and continues updating its exact playback position. */
    fun saveNamedQueue(name: String) = scope.launch {
        val clean = name.trim().take(50)
        if (clean.isEmpty()) return@launch
        val player = connect()
        if (player.mediaItemCount == 0) return@launch
        snapshotQueue(player, force = true)
        activeQueueName = clean
        saved.edit().putString("activeQueueName", clean).apply()
        snapshotQueue(player, force = true)
        _namedQueueNames.value = readQueueNames()
    }

    fun restoreNamedQueue(name: String) = scope.launch {
        val player = connect()
        val data = runCatching {
            JSONObject(saved.getString("namedQueues", "{}") ?: "{}").optJSONObject(name)?.toString()
        }.getOrNull() ?: return@launch
        // Save the previous named session before switching to another one.
        snapshotQueue(player, force = true)
        val restored = restoreSnapshot(player, data)
        if (restored) {
            activeQueueName = name
            saved.edit().putString("activeQueueName", name).apply()
            snapshotQueue(player, force = true)
            publish(player)
        }
    }

    fun deleteNamedQueue(name: String) {
        val data = runCatching { JSONObject(saved.getString("namedQueues", "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        data.remove(name)
        if (activeQueueName == name) activeQueueName = null
        saved.edit().putString("namedQueues", data.toString())
            .putString("activeQueueName", activeQueueName).apply()
        _namedQueueNames.value = readQueueNames()
    }

    private fun snapshotQueue(player: Player, force: Boolean = false) {
        if (player.mediaItemCount == 0 || player.currentMediaItemIndex < 0) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && !PlaybackRefreshPolicy.shouldPersist(lastSnapshotMs, now)) return
        lastSnapshotMs = now
        val data = JSONObject().put("ids", JSONArray().apply {
            for (index in 0 until player.mediaItemCount) {
                player.getMediaItemAt(index).mediaId.toLongOrNull()?.let(::put)
            }
        }).put("index", player.currentMediaItemIndex)
            .put("currentId", player.currentMediaItem?.mediaId?.toLongOrNull())
            .put("position", player.currentPosition.coerceAtLeast(0L))
            .put("shuffle", player.shuffleModeEnabled)
            .put("repeat", player.repeatMode)
        // All calls are on the controller's main thread. apply() publishes the new value
        // immediately and writes it to disk asynchronously. This prevents reordered
        // IO coroutine jobs from resurrecting an older queue snapshot after pause/seek.
        val update = saved.edit().putString("queue", data.toString())
        activeQueueName?.let { name ->
            val stored = runCatching { JSONObject(saved.getString("namedQueues", "{}") ?: "{}") }
                .getOrDefault(JSONObject())
            stored.put(name, data)
            update.putString("namedQueues", stored.toString())
        }
        update.apply()
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
        if (settings.previousBehavior.value == "previous") p.seekToPreviousMediaItem()
        else if (p.currentPosition > 3_000) p.seekTo(0) else p.seekToPreviousMediaItem()
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
