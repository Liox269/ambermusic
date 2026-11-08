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
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.liox.ambermusic.ui.LibraryAndArtistScreens
import dev.liox.ambermusic.ui.NowPlayingScreen
import dev.liox.ambermusic.ui.ExtrasScreen
import dev.liox.ambermusic.ui.LocalAmberAccent
import dev.liox.ambermusic.ui.LocalAmberPalette
import dev.liox.ambermusic.ui.LocalAmberCorners
import dev.liox.ambermusic.ui.LocalAmberIconStyle
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import dev.liox.ambermusic.ui.AmberPalettes
import dev.liox.ambermusic.ui.design.ContrastPolicy
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.liox.ambermusic.settings.AmberSettings
import dev.liox.ambermusic.profiles.ProfileStore
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
        // Leave refresh rate adaptive: no global 120 Hz request for a music player.
        setContent {
            val amberSettings: AmberSettings = koinInject()
            val profiles: ProfileStore = koinInject()
            val accentHex by amberSettings.accentHex.collectAsState()
            val fontChoice by amberSettings.fontChoice.collectAsState()
            val corners by amberSettings.corners.collectAsState()
            val iconStyle by amberSettings.iconStyle.collectAsState()
            val theme by amberSettings.theme.collectAsState()
            val visualStyle by amberSettings.visualStyle.collectAsState()
            val originalType = remember { Typography() }
            val displayFamily = if (fontChoice == "expressive") FontFamily.Serif else FontFamily.SansSerif
            val appType = remember(displayFamily) {
                originalType.copy(
                    bodyLarge = originalType.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
                    bodyMedium = originalType.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
                    bodySmall = originalType.bodySmall.copy(fontFamily = FontFamily.SansSerif),
                    titleLarge = originalType.titleLarge.copy(fontFamily = displayFamily),
                    titleMedium = originalType.titleMedium.copy(fontFamily = displayFamily),
                    titleSmall = originalType.titleSmall.copy(fontFamily = displayFamily),
                    labelLarge = originalType.labelLarge.copy(fontFamily = FontFamily.SansSerif),
                    labelMedium = originalType.labelMedium.copy(fontFamily = FontFamily.SansSerif),
                    labelSmall = originalType.labelSmall.copy(fontFamily = FontFamily.SansSerif)
                )
            }
            val palette = remember(theme, visualStyle, accentHex) {
                val base = AmberPalettes.styled(AmberPalettes.fromId(theme), visualStyle)
                val customAccent = dev.liox.ambermusic.profiles.ProfileRules.hexColorOrNull(accentHex)
                if (customAccent != null) base.copy(accent = Color(android.graphics.Color.parseColor(customAccent)))
                else base
            }
            val accent = palette.accent
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = visualStyle == "light"
                controller.isAppearanceLightNavigationBars = visualStyle == "light"
            }
            val scheme = if (visualStyle == "light") {
                androidx.compose.material3.lightColorScheme(
                    background = palette.background, surface = palette.surface,
                    surfaceVariant = palette.surfaceRaised,
                    surfaceContainer = palette.surface, surfaceContainerHigh = palette.surfaceRaised,
                    primary = accent, secondary = palette.secondary, onPrimary = Color(ContrastPolicy.onColor(accent.toArgb())),
                    onSurface = palette.foreground, onSurfaceVariant = palette.muted
                )
            } else androidx.compose.material3.darkColorScheme(
                background = palette.background, surface = palette.surface,
                surfaceVariant = palette.surfaceRaised,
                surfaceContainer = palette.surface, surfaceContainerHigh = palette.surfaceRaised,
                primary = accent, secondary = palette.secondary, onPrimary = Color(ContrastPolicy.onColor(accent.toArgb())),
                onSurface = palette.foreground, onSurfaceVariant = palette.muted
            )
            MaterialTheme(typography = appType, colorScheme = scheme) {
                CompositionLocalProvider(LocalAmberAccent provides accent, LocalAmberPalette provides palette,
                    LocalAmberCorners provides (if (corners == "square") 9.dp else 22.dp),
                    LocalAmberIconStyle provides iconStyle) {
                    ProvideTextStyle(value = TextStyle(fontFamily = FontFamily.SansSerif)) {
                        Surface(Modifier.fillMaxSize(), color = palette.background) {
                            AmberMusicRoot(onExitApp = { finishAndRemoveTask() }, profiles = profiles)
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
    profiles: ProfileStore,
    viewModel: MusicViewModel = koinViewModel()
) {
    val library by viewModel.libraryUiState.collectAsState()
    val player by viewModel.playerUiState.collectAsState()
    val queueIds by viewModel.queueIds.collectAsState()
    val namedQueueNames by viewModel.namedQueues.collectAsState()
    val currentLyrics by viewModel.currentLyrics.collectAsState()
    val songsById = remember(library.songs) { library.songs.associateBy { it.id } }
    val queueSongs = remember(queueIds, songsById) { queueIds.mapNotNull(songsById::get) }
    val stopAndExit = { viewModel.stopAndExit(onExitApp) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val reduceMotion by viewModel.reduceMotion.collectAsState()
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
    var openExtrasOnProfiles by rememberSaveable { mutableStateOf(false) }
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
    val audioSessionId by viewModel.audioSessionId.collectAsState()
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.setCustomArtwork(uri)
    }
    val lyricsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importLyrics(uri)
    }
    BackHandler(enabled = showExtras) { showExtras = false }
    AnimatedContent(
        targetState = showPlayer,
        transitionSpec = {
            if (targetState) {
                (slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(if (reduceMotion) 0 else 290, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(if (reduceMotion) 0 else 220))) togetherWith
                    (slideOutVertically(
                        targetOffsetY = { -it / 7 },
                        animationSpec = tween(if (reduceMotion) 0 else 270, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(if (reduceMotion) 0 else 200)))
            } else {
                (slideInVertically(
                    initialOffsetY = { -it / 8 },
                    animationSpec = tween(if (reduceMotion) 0 else 290, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(if (reduceMotion) 0 else 220))) togetherWith
                    (slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(if (reduceMotion) 0 else 270, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(if (reduceMotion) 0 else 200)))
            }
        },
        label = "Reproductor expandible 120 Hz"
    ) { playerOpen ->
    if (playerOpen) {
        NowPlayingScreen(
            state = player,
            categories = library.categories,
            playlists = library.playlists,
            queueSongs = queueSongs,
            sleepMinutes = viewModel.sleepMinutes.collectAsState().value,
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
            onSleepTimer = viewModel::setSleepTimer,
            onMoveQueue = viewModel::moveQueueItem,
            lyrics = currentLyrics,
            onImportLyrics = { lyricsPicker.launch(arrayOf("text/plain", "application/octet-stream", "*/*")) },
            onFetchLrclib = { viewModel.fetchLyricsFromLrclib { message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            } },
            onAdjustLyricsOffset = viewModel::adjustLyricsOffset,
            onClearLyrics = viewModel::clearLyrics,
            namedQueueNames = namedQueueNames,
            onSaveNamedQueue = viewModel::saveNamedQueue,
            onRestoreNamedQueue = viewModel::restoreNamedQueue,
            onDeleteNamedQueue = viewModel::deleteNamedQueue,
            onSaveQueuePlaylist = { name -> viewModel.saveCurrentQueueAsPlaylist(name) { message ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            } },
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
        Box(Modifier.fillMaxSize().background(LocalAmberPalette.current.background).safeDrawingPadding()) {
            ExtrasScreen(library = library, viewModel = viewModel,
                onBack = { showExtras = false }, onPlayer = { showExtras = false; showPlayer = true },
                startOnSettings = openExtrasOnSettings, startOnProfiles = openExtrasOnProfiles,
                onOpenFavorites = { favoritesOpenRequest++; showExtras = false; selectedArtist = null })
        }
    } else {
        pageStateHolder.SaveableStateProvider("music_library") {
        Box(Modifier.fillMaxSize().background(LocalAmberPalette.current.background).safeDrawingPadding()) {
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
                onOpenExtras = { openExtrasOnSettings = false; openExtrasOnProfiles = false; showExtras = true },
                onOpenSettings = { openExtrasOnSettings = true; openExtrasOnProfiles = false; showExtras = true },
                onOpenProfiles = { openExtrasOnSettings = false; openExtrasOnProfiles = true; showExtras = true },
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
                        .shadow(10.dp, RoundedCornerShape(LocalAmberCorners.current))
                        .clip(RoundedCornerShape(LocalAmberCorners.current))
                        .background(LocalAmberPalette.current.surfaceRaised.copy(alpha = 0.96f))
                ) {
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth().clickable { showPlayer = true }
                            .padding(start = 10.dp, end = 5.dp, top = 8.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        dev.liox.ambermusic.ui.SongArtwork(song = song, size = 46.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song?.title.orEmpty(), color = LocalAmberPalette.current.foreground,
                                fontWeight = FontWeight.SemiBold, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(3.dp))
                            Text(song?.artistDisplay.orEmpty(), color = LocalAmberPalette.current.muted,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = viewModel::togglePlayPause) {
                            Icon(
                                if (LocalAmberIconStyle.current == "outlined") {
                                    if (player.playback.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow
                                } else if (player.playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Pausar o reproducir", tint = LocalAmberPalette.current.foreground,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        IconButton(onClick = viewModel::next) {
                            Icon(if (LocalAmberIconStyle.current == "outlined") Icons.Outlined.SkipNext else Icons.Filled.SkipNext,
                                contentDescription = "Siguiente", tint = LocalAmberPalette.current.foreground)
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
