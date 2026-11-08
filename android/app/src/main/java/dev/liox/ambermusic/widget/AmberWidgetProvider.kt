package dev.liox.ambermusic.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.RemoteViews
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.liox.ambermusic.MainActivity
import dev.liox.ambermusic.R
import dev.liox.ambermusic.playback.MusicPlaybackService

/** Native RemoteViews widget: works with HyperOS and does not run a permanent polling loop. */
class AmberWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context, null, null, false)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action !in listOf(ACTION_PLAY, ACTION_PREV, ACTION_NEXT)) return
        val token = SessionToken(context, ComponentName(context, MusicPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            try {
                val controller = future.get()
                when (action) {
                    ACTION_PLAY -> if (controller.isPlaying) controller.pause() else {
                        if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
                        controller.play()
                    }
                    ACTION_PREV -> controller.seekToPreviousMediaItem()
                    ACTION_NEXT -> controller.seekToNextMediaItem()
                }
                update(context, controller.mediaMetadata.title?.toString(),
                    controller.mediaMetadata.artist?.toString(), controller.isPlaying,
                    artwork = controller.mediaMetadata.artworkData,
                    positionMs = controller.currentPosition,
                    durationMs = controller.duration)
            } catch (_: Exception) {
                update(context, null, null, false)
            } finally {
                MediaController.releaseFuture(future)
            }
        }, context.mainExecutor)
    }

    companion object {
        const val ACTION_PLAY = "dev.liox.ambermusic.widget.PLAY"
        const val ACTION_PREV = "dev.liox.ambermusic.widget.PREV"
        const val ACTION_NEXT = "dev.liox.ambermusic.widget.NEXT"

        fun update(context: Context, title: String?, artist: String?, playing: Boolean,
            artwork: ByteArray? = null, positionMs: Long = 0L, durationMs: Long = 0L) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, AmberWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val view = RemoteViews(context.packageName, R.layout.amber_widget)
            view.setTextViewText(R.id.widget_title, title ?: "AmberMusic")
            view.setTextViewText(R.id.widget_artist, artist ?: "Música local")
            view.setTextViewText(R.id.widget_play, if (playing) "Ⅱ" else "▶")
            if (artwork != null && artwork.size <= 1_500_000) {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(artwork, 0, artwork.size, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 720 || bounds.outHeight / sample > 720) sample *= 2
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
                }
                val bitmap = BitmapFactory.decodeByteArray(artwork, 0, artwork.size, options)
                if (bitmap != null) view.setImageViewBitmap(R.id.widget_cover, bitmap)
                else view.setImageViewResource(R.id.widget_cover, R.mipmap.ic_launcher)
            } else view.setImageViewResource(R.id.widget_cover, R.mipmap.ic_launcher)
            val position = positionMs.coerceAtLeast(0L)
            view.setChronometer(R.id.widget_time,
                android.os.SystemClock.elapsedRealtime() - position, null, playing)
            val duration = durationMs.takeIf { it > 0L } ?: 1L
            view.setProgressBar(R.id.widget_progress, 1000,
                (position.toDouble() / duration * 1000).toInt().coerceIn(0, 1000), false)
            val intent = Intent(context, MainActivity::class.java)
            val open = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            view.setOnClickPendingIntent(R.id.amber_widget_root, open)
            listOf(R.id.widget_play to ACTION_PLAY, R.id.widget_previous to ACTION_PREV,
                R.id.widget_next to ACTION_NEXT).forEachIndexed { index, pair ->
                val broadcast = Intent(context, AmberWidgetProvider::class.java).setAction(pair.second)
                val pending = PendingIntent.getBroadcast(context, index + 10, broadcast,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                view.setOnClickPendingIntent(pair.first, pending)
            }
            manager.updateAppWidget(ids, view)
        }
    }
}
