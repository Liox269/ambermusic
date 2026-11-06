package dev.liox.ambermusic

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.Text
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.liox.ambermusic.ui.LibraryAndArtistScreens
import dev.liox.ambermusic.ui.NowPlayingScreen
import dev.liox.ambermusic.ui.ExtrasScreen
import dev.liox.ambermusic.ui.LocalAmberAccent
import dev.liox.ambermusic.ui.LocalAmberPalette
import dev.liox.ambermusic.ui.AmberPalettes
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.liox.ambermusic.settings.AmberSettings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import org.koin.compose.koinInject
import dev.liox.ambermusic.viewmodel.MusicViewModel
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        // Ask the device for its fastest 120 Hz-compatible mode. HyperOS keeps final control.
        val availableRates = display?.supportedModes?.map { it.refreshRate }.orEmpty()
        val requestedRate = availableRates.filter { it <= 120.5f }.maxOrNull()
            ?: availableRates.maxOrNull() ?: 60f
        window.attributes = window.attributes.apply { preferredRefreshRate = requestedRate }
        setContent {
            val originalType = Typography()
            val appType = originalType.copy(
                bodyLarge = originalType.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
                bodyMedium = originalType.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
                bodySmall = originalType.bodySmall.copy(fontFamily = FontFamily.SansSerif),
                titleLarge = originalType.titleLarge.copy(fontFamily = FontFamily.SansSerif),
                titleMedium = originalType.titleMedium.copy(fontFamily = FontFamily.SansSerif),
                titleSmall = originalType.titleSmall.copy(fontFamily = FontFamily.SansSerif),
                labelLarge = originalType.labelLarge.copy(fontFamily = FontFamily.SansSerif),
                labelMedium = originalType.labelMedium.copy(fontFamily = FontFamily.SansSerif),
                labelSmall = originalType.labelSmall.copy(fontFamily = FontFamily.SansSerif)
            )
            val amberSettings: AmberSettings = koinInject()
            val theme by amberSettings.theme.collectAsState()
            val palette = remember(theme) { AmberPalettes.fromId(theme) }
            val accent = palette.accent
            MaterialTheme(
                typography = appType,
                colorScheme = androidx.compose.material3.darkColorScheme(
                    background = Color.Black, surface = Color.Black,
                    surfaceVariant = palette.surfaceRaised,
                    surfaceContainer = palette.surface, surfaceContainerHigh = palette.surfaceRaised,
                    primary = accent, secondary = palette.secondary, onPrimary = Color.Black,
                    onSurface = Color.White, onSurfaceVariant = palette.muted
                )
            ) {
                CompositionLocalProvider(LocalAmberAccent provides accent, LocalAmberPalette provides palette) {
                    ProvideTextStyle(value = TextStyle(fontFamily = FontFamily.SansSerif)) {
                        Surface(Modifier.fillMaxSize().preferredFrameRate(120f), color = Color.Black) {
                            AmberMusicRoot(onExitApp = { finishAndRemoveTask() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AmberMusicRoot(
    onExitApp: () -> Unit,
    viewModel: MusicViewModel = koinViewModel()
) {
    val library by viewModel.libraryUiState.collectAsState()
    val player by viewModel.playerUiState.collectAsState()
    val stopAndExit = { viewModel.stopAndExit(onExitApp) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.savePlaybackSession()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val pageStateHolder = rememberSaveableStateHolder()
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var showExtras by rememberSaveable { mutableStateOf(false) }
    var openExtrasOnSettings by rememberSaveable { mutableStateOf(false) }
    var selectedArtist by rememberSaveable { mutableStateOf<Long?>(null) }
    var favoritesOpenRequest by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val audioPermission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else Manifest.permission.READ_EXTERNAL_STORAGE
    var hasPermission by remember { mutableStateOf(
        context.checkSelfPermission(audioPermission) == PackageManager.PERMISSION_GRANTED
    ) }
    var checkedOnce by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        hasPermission = context.checkSelfPermission(audioPermission) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) viewModel.scanLocalMusic()
    }
    val requestPermissions: () -> Unit = {
        val permissions = buildList {
            if (context.checkSelfPermission(audioPermission) != PackageManager.PERMISSION_GRANTED) add(audioPermission)
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (permissions.isEmpty()) {
            hasPermission = true
            viewModel.scanLocalMusic()
        } else launcher.launch(permissions.toTypedArray())
    }
    LaunchedEffect(Unit) {
        if (!checkedOnce) {
            checkedOnce = true
            requestPermissions()
        }
    }
    val lyrics by viewModel.currentLyrics.collectAsState()
    val audioSessionId by viewModel.audioSessionId.collectAsState()
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.setCustomArtwork(uri)
    }
    val lrcPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importLyrics(uri)
    }
    BackHandler(enabled = showExtras) { showExtras = false }
    AnimatedContent(
        targetState = showPlayer,
        transitionSpec = {
            if (targetState) {
                (slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(330, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(260))) togetherWith
                    (slideOutVertically(
                        targetOffsetY = { -it / 7 },
                        animationSpec = tween(310, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(220)))
            } else {
                (slideInVertically(
                    initialOffsetY = { -it / 8 },
                    animationSpec = tween(330, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(260))) togetherWith
                    (slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(310, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(220)))
            }
        },
        label = "Reproductor expandible 120 Hz"
    ) { playerOpen ->
    if (playerOpen) {
        NowPlayingScreen(
            state = player,
            categories = library.categories,
            playlists = library.playlists,
            queueSongs = viewModel.queueIds.collectAsState().value.mapNotNull { id -> library.songs.firstOrNull { it.id == id } },
            lyrics = lyrics,
            sleepMinutes = viewModel.sleepMinutes.collectAsState().value,
            onImportLyrics = { lrcPicker.launch(arrayOf("*/*")) },
            onPickCover = { coverPicker.launch(arrayOf("image/*")) },
            onOpenEqualizer = {
                val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                }
                runCatching { context.startActivity(intent) }
                    .onFailure { Toast.makeText(context, "No hay ecualizador del sistema disponible", Toast.LENGTH_SHORT).show() }
            },
            onRemoveCover = viewModel::clearCustomArtwork,
            onRemoveLyrics = viewModel::clearLyrics,
            onAdjustLyrics = viewModel::adjustLyricsOffset,
            onEditLyrics = viewModel::saveEditedLyrics,
            onSleepTimer = viewModel::setSleepTimer,
            onMoveQueue = viewModel::moveQueueItem,
            onMetadataEdit = viewModel::changeDisplayMetadata,
            onAddPlaylist = { song, playlist -> viewModel.addToPlaylist(song, playlist) },
            onBack = { showPlayer = false },
            onArtist = { id -> selectedArtist = id; showPlayer = false },
            onPlayPause = viewModel::togglePlayPause,
            onSeek = viewModel::seekTo,
            onPrevious = viewModel::previous,
            onNext = viewModel::next,
            onShuffle = viewModel::toggleShuffle,
            onRepeat = viewModel::toggleRepeat,
            onFavorite = viewModel::toggleFavorite,
            onOpenFavorites = { favoritesOpenRequest++; showPlayer = false; showExtras = false; selectedArtist = null },
            onAddCategory = { songId, categoryId -> viewModel.addToCategory(songId, categoryId) },
            onExitApp = stopAndExit
        )
    } else if (showExtras) {
        Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            ExtrasScreen(library = library, viewModel = viewModel,
                onBack = { showExtras = false }, onPlayer = { showExtras = false; showPlayer = true },
                startOnSettings = openExtrasOnSettings)
        }
    } else {
        pageStateHolder.SaveableStateProvider("music_library") {
        Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            LibraryAndArtistScreens(
                state = library,
                playing = player,
                viewModel = viewModel,
                onOpenPlayer = { showPlayer = true },
                externalArtistId = selectedArtist,
                onArtistNavigated = { selectedArtist = null },
                hasAudioPermission = hasPermission,
                onRequestPermission = requestPermissions,
                onExitApp = stopAndExit,
                onOpenExtras = { openExtrasOnSettings = false; showExtras = true },
                onOpenSettings = { openExtrasOnSettings = true; showExtras = true },
                favoritesOpenRequest = favoritesOpenRequest
            )
            if (player.song != null) {
                val song = player.song
                val duration = (player.progress.durationMs.takeIf { it > 0L } ?: song?.durationMs ?: 0L)
                val progress = if (duration > 0L) {
                    (player.progress.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                } else 0f
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .pointerInput(Unit) {
                            var verticalDelta = 0f
                            detectVerticalDragGestures(
                                onDragStart = { verticalDelta = 0f },
                                onDragEnd = { if (verticalDelta < -65.dp.toPx()) showPlayer = true },
                                onVerticalDrag = { _, amount -> verticalDelta += amount }
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(LocalAmberPalette.current.surfaceRaised)
                ) {
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth().clickable { showPlayer = true }
                            .padding(start = 10.dp, end = 5.dp, top = 8.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        dev.liox.ambermusic.ui.SongArtwork(song = song, size = 46.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song?.title.orEmpty(), color = Color.White,
                                fontWeight = FontWeight.SemiBold, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(3.dp))
                            Text(song?.artistDisplay.orEmpty(), color = Color(0xFF9CB4CA),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = viewModel::togglePlayPause) {
                            Icon(
                                if (player.playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Pausar o reproducir", tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        IconButton(onClick = viewModel::next) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Siguiente", tint = Color.White)
                        }
                    }
                    Box(Modifier.fillMaxWidth().height(3.dp).background(LocalAmberPalette.current.surface)) {
                        Box(Modifier.fillMaxWidth(progress).height(3.dp).background(LocalAmberAccent.current))
                    }
                }
            }
        }
        }
    }
    }
}
