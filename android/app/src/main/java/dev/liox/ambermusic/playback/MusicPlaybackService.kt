package dev.liox.ambermusic.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import dev.liox.ambermusic.settings.AmberSettings
import dev.liox.ambermusic.audio.ReplayGainReader
import dev.liox.ambermusic.widget.AmberWidgetProvider
import android.net.Uri
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.liox.ambermusic.MainActivity
import dev.liox.ambermusic.R
import dev.liox.ambermusic.artwork.ArtworkRepository
import dev.liox.ambermusic.data.local.MusicDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

@OptIn(UnstableApi::class)
class MusicPlaybackService : MediaSessionService() {
    private val dao: MusicDao by inject()
    private val artwork: ArtworkRepository by inject()
    private val settings: AmberSettings by inject()
    private val gainReader: ReplayGainReader by inject()
    private var gainJob: Job? = null
    private var rampJob: Job? = null
    private var widgetJob: Job? = null
    private var gainVolume = 1f
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private var artworkJob: Job? = null
    private var audioId: Int = C.AUDIO_SESSION_ID_UNSET

    override fun onCreate() {
        super.onCreate()
        val attributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(attributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        serviceScope.launch {
            combine(settings.offload, settings.replayGain) { enabled, gain -> enabled && gain == "off" }
                .collectLatest { enabled ->
                val preferences = TrackSelectionParameters.AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(
                        if (enabled) TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                        else TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                    )
                    .setIsGaplessSupportRequired(true)
                    .build()
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setAudioOffloadPreferences(preferences).build()
            }
        }
        val intent = Intent(this, MainActivity::class.java)
        val activity = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(PlaybackCallback())
            .setSessionActivity(activity)
            .setSessionExtras(Bundle().apply { putInt(EXTRA_AUDIO_SESSION_ID, audioId) })
            .build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.playback_channel_name)
                .build()
        )
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                loadArtworkForCurrentItem()
                refreshGain()
            }

            override fun onEvents(player: Player, events: Player.Events) {
                val meta = player.mediaMetadata
                widgetJob?.cancel()
                val title = meta.title?.toString()
                val artist = meta.artist?.toString()
                val playing = player.isPlaying
                val cover = meta.artworkData
                widgetJob = serviceScope.launch(Dispatchers.IO) {
                    AmberWidgetProvider.update(this@MusicPlaybackService,
                        title, artist, playing, cover)
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                audioId = audioSessionId
                mediaSession?.setSessionExtras(Bundle().apply {
                    putInt(EXTRA_AUDIO_SESSION_ID, audioSessionId)
                })
            }
        })
        serviceScope.launch {
            settings.replayGain.collectLatest { refreshGain() }
        }
        refreshGain()
    }

    private fun refreshGain() {
        gainJob?.cancel()
        rampJob?.cancel()
        val item = player.currentMediaItem
        player.volume = if (settings.smoothTransition.value && player.isPlaying) 0f else 1f
        gainVolume = 1f
        if (item == null) return
        gainJob = serviceScope.launch {
            val mode = settings.replayGain.value
            val id = item.mediaId.toLongOrNull() ?: return@launch
            val song = withContext(Dispatchers.IO) { dao.getSong(id) } ?: return@launch
            val volume = gainReader.volumeFactor(Uri.parse(song.contentUri), song.displayName, mode)
            if (player.currentMediaItem?.mediaId != item.mediaId) return@launch
            gainVolume = volume
            if (settings.smoothTransition.value && player.isPlaying) {
                rampJob = serviceScope.launch {
                    repeat(8) { step ->
                        player.volume = gainVolume * ((step + 1) / 8f)
                        delay(35L)
                    }
                }
            } else player.volume = gainVolume
        }
    }

    private fun loadArtworkForCurrentItem() {
        artworkJob?.cancel()
        val item = player.currentMediaItem ?: return
        if (item.mediaMetadata.artworkData != null) return
        val id = item.mediaId.toLongOrNull() ?: return
        artworkJob = serviceScope.launch {
            val song = withContext(Dispatchers.IO) { dao.getSong(id) } ?: return@launch
            val cover = artwork.load(song) ?: return@launch
            val position = player.currentMediaItemIndex
            val current = player.currentMediaItem ?: return@launch
            if (current.mediaId != id.toString() || position < 0) return@launch
            val metadata = current.mediaMetadata.buildUpon()
                .setArtworkUri(cover.uri)
                .setArtworkData(cover.encodedJpeg, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                .build()
            player.replaceMediaItem(position, current.buildUpon().setMediaMetadata(metadata).build())
        }
    }

    private inner class PlaybackCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val commands = if (controller.isTrusted || controller.packageName == packageName) {
                MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
            } else {
                Player.Commands.Builder()
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                    .add(Player.COMMAND_GET_TIMELINE)
                    .build()
            }
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailablePlayerCommands(commands)
                .build()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** Recents dismissal pauses audio without erasing the saved queue. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        player.pause()
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        artworkJob?.cancel()
        gainJob?.cancel()
        rampJob?.cancel()
        widgetJob?.cancel()
        AmberWidgetProvider.update(this, null, null, false)
        serviceScope.cancel()
        mediaSession?.release()
        mediaSession = null
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_AUDIO_SESSION_ID = "dev.liox.ambermusic.AUDIO_SESSION_ID"
    }
}
