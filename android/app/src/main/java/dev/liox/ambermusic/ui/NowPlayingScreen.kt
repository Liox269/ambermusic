package dev.liox.ambermusic.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.zIndex
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import dev.liox.ambermusic.artwork.ArtworkRepository
import androidx.palette.graphics.Palette
import dev.liox.ambermusic.visual.ImmersiveBackdrop
import dev.liox.ambermusic.settings.AmberSettings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import androidx.compose.ui.layout.ContentScale
import dev.liox.ambermusic.data.local.ArtistEntity
import dev.liox.ambermusic.data.local.CategoryEntity
import dev.liox.ambermusic.viewmodel.PlayerUiState
import dev.liox.ambermusic.data.local.PlaylistEntity
import dev.liox.ambermusic.data.local.SongEntity
import dev.liox.ambermusic.lyrics.LyricLine
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import kotlin.math.roundToLong

private data class PresentedCover(val songId: Long?, val artwork: Any?, val title: String)

private val amber: Color
    @Composable get() = LocalAmberAccent.current
private val muted: Color
    @Composable get() = LocalAmberPalette.current.muted
private val glass: Color
    @Composable get() = LocalAmberPalette.current.surfaceRaised.copy(alpha = 0.68f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    categories: List<CategoryEntity>,
    playlists: List<PlaylistEntity>,
    queueSongs: List<SongEntity>,
    lyrics: List<LyricLine>,
    sleepMinutes: Int,
    onImportLyrics: () -> Unit,
    onPickCover: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onRemoveCover: () -> Unit,
    onRemoveLyrics: () -> Unit,
    onAdjustLyrics: (Long) -> Unit,
    onEditLyrics: (String) -> Unit,
    onSleepTimer: (Int) -> Unit,
    onMoveQueue: (Int, Int) -> Unit,
    onMetadataEdit: (Long, String, String, String) -> Unit,
    onAddPlaylist: (Long, Long) -> Unit,
    onBack: () -> Unit,
    onArtist: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onFavorite: () -> Unit,
    onOpenFavorites: () -> Unit,
    onAddCategory: (Long, Long) -> Unit,
    onExitApp: () -> Unit
) {
    val context = LocalContext.current
    var showArtists by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var showRoute by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var showLyricsEdit by remember { mutableStateOf(false) }
    BackHandler(enabled = !showArtists && !showOptions && !showRoute && !showLyrics &&
        !showQueue && !showTimer && !showEdit && !showLyricsEdit, onBack = onBack)
    val localSettings: AmberSettings = koinInject()
    val immersiveEnabled by localSettings.immersive.collectAsState()
    val ambientRepo: ImmersiveBackdrop = koinInject()
    val song = state.song
    val artworkRepository: ArtworkRepository = koinInject()
    val coverVersion by artworkRepository.version.collectAsState()
    val cachedCover by produceState<java.io.File?>(null, song?.id, song?.modifiedAtSeconds, song?.albumId, coverVersion) {
        value = if (song == null) null else artworkRepository.fileFor(song)
    }
    val coverUri: Any? = cachedCover ?: state.artworkUri
    // Preserve the previous ambient field until the next pre-rendered image is ready.
    // This prevents the full-screen flash / square texture seen in v1.1.0.
    var backdrop by remember { mutableStateOf<dev.liox.ambermusic.visual.AmbientArtwork?>(null) }
    LaunchedEffect(song?.id, coverVersion, immersiveEnabled) {
        // Resolve the cover completely before replacing the previous ambient layer.
        // Never expose a low-resolution unblurred placeholder between tracks.
        backdrop = when {
            !immersiveEnabled || song == null -> null
            else -> ambientRepo.forCover(artworkRepository.fileFor(song))
        }
    }
    // Prepare the following song while the current one plays: track switches can crossfade
    // immediately from disk cache instead of waiting for a new background render.
    LaunchedEffect(song?.id, queueSongs.map { it.id }, immersiveEnabled) {
        if (immersiveEnabled && song != null && queueSongs.size > 1) {
            val index = queueSongs.indexOfFirst { it.id == song.id }
            if (index >= 0) {
                val following = queueSongs[(index + 1) % queueSongs.size]
                delay(950)
                ambientRepo.forCover(artworkRepository.fileFor(following))
            }
        }
    }
    val atmosphere by animateColorAsState(
        targetValue = backdrop?.let { Color(it.primary) } ?: Color(0xFF15273C),
        animationSpec = tween(1050, easing = FastOutSlowInEasing), label = "Atmósfera de portada"
    )
    val atmosphereDark by animateColorAsState(
        targetValue = backdrop?.let { Color(it.dark) } ?: Color.Black,
        animationSpec = tween(1050, easing = FastOutSlowInEasing), label = "Fondo de álbum"
    )

    Box(Modifier.fillMaxSize().preferredFrameRate(120f).background(Color.Black)) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to atmosphere.copy(alpha = 0.47f),
                    0.48f to atmosphereDark.copy(alpha = 0.30f),
                    1f to Color.Black
                )
            )
        )
        if (immersiveEnabled) {
            Crossfade(
                targetState = backdrop?.file,
                animationSpec = tween(1100, easing = FastOutSlowInEasing),
                label = "Transición ambiental sin bloques"
            ) { image ->
                if (image != null) {
                    AsyncImage(
                        model = image,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize().alpha(0.92f)
                    )
                }
            }
            Box(Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(atmosphere.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(550f, 390f), radius = 1350f
                )
            ))
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to atmosphereDark.copy(alpha = 0.14f),
                    0.55f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.65f)
                )
            ))
        }
        // Suave matiz nocturno: no modifica ni recalcula el blur generado por ImmersiveBackdrop.
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0.0f to LocalAmberAccent.current.copy(alpha = 0.065f),
                0.54f to Color.Transparent,
                1.0f to Color.Black.copy(alpha = 0.18f)
            )
        ))
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.0f to Color.Black.copy(alpha = 0.33f),
                    0.32f to Color.Black.copy(alpha = 0.03f),
                    0.62f to Color.Black.copy(alpha = 0.11f),
                    1f to Color.Black.copy(alpha = 0.79f)
                )
            )
        )
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding()
                .pointerInput(onBack) {
                    var swipeDistance = 0f
                    detectVerticalDragGestures(
                        onDragStart = { swipeDistance = 0f },
                        onDragEnd = { if (swipeDistance > 110.dp.toPx()) onBack() },
                        onVerticalDrag = { _, drag -> swipeDistance = (swipeDistance + drag).coerceAtLeast(0f) }
                    )
                }
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth().height(80.dp), verticalAlignment = Alignment.CenterVertically) {
                ArtistBubblesRow(state.artists, state.artistInitials, { showArtists = true })
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Volver a biblioteca",
                        tint = muted, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(10.dp))
                CircleAction(onClick = { showRoute = true }, description = "Salida de audio") {
                    Icon(
                        if (state.route.bluetooth) Icons.Filled.BluetoothAudio else Icons.Filled.Speaker,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.weight(0.48f))
            AnimatedContent(
                targetState = PresentedCover(song?.id, coverUri, song?.title.orEmpty()),
                contentKey = { it.songId },
                label = "Cambio de carátula premium",
                transitionSpec = {
                    (fadeIn(tween(730, easing = FastOutSlowInEasing)) +
                        scaleIn(initialScale = 0.965f, animationSpec = tween(730, easing = FastOutSlowInEasing))) togetherWith
                        (fadeOut(tween(450)) +
                            scaleOut(targetScale = 1.035f, animationSpec = tween(600, easing = FastOutSlowInEasing)))
                }
            ) { displayed ->
                if (displayed.artwork != null) {
                    AsyncImage(
                        model = displayed.artwork,
                        contentDescription = "Carátula de ${displayed.title}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                            .shadow(8.dp, RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp))
                            .pointerInput(song?.id) {
                                var delta = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { delta = 0f },
                                    onDragEnd = {
                                        if (delta < -90f) onNext()
                                        if (delta > 90f) onPrevious()
                                    },
                                    onHorizontalDrag = { change, amount ->
                                        change.consume()
                                        delta += amount
                                    }
                                )
                            }
                    )
                } else {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp)).background(Color(0xFF1E1E24)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Speaker, contentDescription = null, tint = muted, modifier = Modifier.size(70.dp))
                    }
                }
            }
            Spacer(Modifier.weight(0.55f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        song?.title ?: "Selecciona una canción", color = Color.White,
                        fontWeight = FontWeight.Bold, fontSize = 20.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        song?.artistDisplay ?: "Biblioteca local",
                        color = muted, fontSize = 14.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier.size(44.dp).clip(CircleShape)
                        .background(if (state.isFavorite) Color(0xFF17324C) else glass)
                        .combinedClickable(
                            enabled = song != null,
                            onClickLabel = if (state.isFavorite) "Quitar de Favoritos" else "Añadir a Favoritos",
                            onLongClickLabel = "Ver categoría Favoritos",
                            onClick = onFavorite,
                            onLongClick = onOpenFavorites
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (state.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (state.isFavorite) "En Favoritos" else "Añadir a Favoritos",
                        tint = if (state.isFavorite) amber else Color.White,
                        modifier = Modifier.size(23.dp)
                    )
                }
            }
            Spacer(Modifier.height(27.dp))
            PillSeekbar(
                positionMs = state.progress.positionMs,
                durationMs = state.progress.durationMs.takeIf { it > 0 } ?: song?.durationMs ?: 0,
                onSeek = onSeek
            )
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTime(state.progress.positionMs), color = muted, fontSize = 11.sp)
                Text(
                    state.qualityLabel,
                    color = amber, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 9.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Text(
                    formatTime(state.progress.durationMs.takeIf { it > 0 } ?: song?.durationMs ?: 0),
                    color = muted, fontSize = 11.sp
                )
            }
            Spacer(Modifier.height(29.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onShuffle) {
                    Icon(Icons.Filled.Shuffle, contentDescription = "Aleatorio",
                        tint = if (state.playback.shuffleModeEnabled) amber else muted,
                        modifier = Modifier.size(26.dp))
                }
                IconButton(onClick = onPrevious) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior", tint = Color.White,
                        modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = onPlayPause, modifier = Modifier.size(76.dp)) {
                    Icon(
                        if (state.playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.playback.isPlaying) "Pausar" else "Reproducir",
                        tint = Color.White, modifier = Modifier.size(53.dp)
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Siguiente", tint = Color.White,
                        modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = onRepeat) {
                    Icon(
                        if (state.playback.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "Repetir",
                        tint = if (state.playback.repeatMode == Player.REPEAT_MODE_OFF) muted else amber,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayerShortcut("Letras", Modifier.weight(1f)) { showLyrics = true }
                PlayerShortcut("Cola", Modifier.weight(1f)) { showQueue = true }
                PlayerShortcut("Temporizador", Modifier.weight(1f)) { showTimer = true }
            }
            Spacer(Modifier.weight(0.15f))
            Row(
                Modifier.fillMaxWidth().height(68.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircleAction(onClick = onBack, description = "Biblioteca") {
                    Icon(Icons.Filled.List, contentDescription = null, tint = Color.White, modifier = Modifier.size(27.dp))
                }
                Column(Modifier.weight(1f).padding(start = 15.dp)) {
                    Text("Reproduciendo desde", color = muted, fontSize = 11.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(state.sourceName, color = Color.White, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                CircleAction(onClick = { showOptions = true }, description = "Opciones") {
                    Icon(Icons.Filled.MoreHoriz, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showArtists) {
        ModalBottomSheet(
            onDismissRequest = { showArtists = false },
            containerColor = Color(0xFF0C1622), contentColor = Color.White
        ) {
            Text("Artistas de la canción", fontWeight = FontWeight.Bold, fontSize = 19.sp,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
            state.artists.forEach { artist ->
                Text(
                    artist.name, color = Color.White,
                    modifier = Modifier.fillMaxWidth().clickable {
                        showArtists = false
                        onArtist(artist.id)
                    }.padding(horizontal = 24.dp, vertical = 17.dp)
                )
            }
            Spacer(Modifier.height(30.dp))
        }
    }
    if (showOptions) {
        ModalBottomSheet(
            onDismissRequest = { showOptions = false },
            containerColor = Color(0xFF0C1622), contentColor = Color.White
        ) {
            Text("Opciones de reproducción", fontWeight = FontWeight.Bold, fontSize = 19.sp,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
            listOf(
                "Cola de reproducción" to { showQueue = true },
                "Temporizador" to { showTimer = true },
                "Ecualizador del sistema" to { onOpenEqualizer() }
            ).forEach { (label, action) ->
                Text(label, color = Color.White,
                    modifier = Modifier.fillMaxWidth().clickable { showOptions = false; action() }
                        .padding(horizontal = 24.dp, vertical = 16.dp))
            }
            Spacer(Modifier.height(30.dp))
        }
    }
    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }, containerColor = Color(0xFF0C1622), contentColor = Color.White) {
            Text("Cola de reproducción", fontWeight = FontWeight.Bold, fontSize = 20.sp,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp))
            LazyColumn(Modifier.fillMaxWidth().height(410.dp)) {
                items(queueSongs.size) { index ->
                    val entry = queueSongs[index]
                    Row(Modifier.fillMaxWidth()
                        .pointerInput(index, queueSongs.size) {
                            var verticalDrag = 0f
                            detectDragGesturesAfterLongPress(
                                onDragStart = { verticalDrag = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    verticalDrag += dragAmount.y
                                    val step = 48.dp.toPx()
                                    if (kotlin.math.abs(verticalDrag) > step) {
                                        val to = (index + if (verticalDrag > 0) 1 else -1)
                                            .coerceIn(0, queueSongs.lastIndex)
                                        if (to != index) onMoveQueue(index, to)
                                        verticalDrag = 0f
                                    }
                                }
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("${index + 1}", color = amber, modifier = Modifier.width(27.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, color = Color.White, maxLines = 1)
                            Text(entry.artistDisplay, color = muted, fontSize = 12.sp, maxLines = 1)
                        }
                        IconButton(onClick = { onMoveQueue(index, index - 1) }, enabled = index > 0) {
                            Icon(Icons.Filled.KeyboardArrowUp, "Subir", tint = Color.White)
                        }
                        IconButton(onClick = { onMoveQueue(index, index + 1) }, enabled = index < queueSongs.lastIndex) {
                            Icon(Icons.Filled.KeyboardArrowDown, "Bajar", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
    if (showLyrics) {
        ModalBottomSheet(onDismissRequest = { showLyrics = false }, containerColor = Color(0xFF0C1622), contentColor = Color.White) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Letras", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { onImportLyrics() }) { Text("Importar .lrc", color = amber) }
            }
            if (lyrics.isEmpty()) {
                Text("Importa un archivo .lrc o .txt desde tu teléfono. No se buscarán letras en internet.",
                    color = muted, modifier = Modifier.padding(22.dp))
            } else {
                val currentIndex = lyrics.indexOfLast { it.timeMs >= 0 && it.timeMs <= state.progress.positionMs }
                LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                    items(lyrics.size) { index ->
                        val line = lyrics[index]
                        Text(line.text, color = if (index == currentIndex) amber else Color.White.copy(alpha = 0.67f),
                            fontSize = if (index == currentIndex) 21.sp else 17.sp,
                            fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth().clickable(enabled = line.timeMs >= 0) {
                                onSeek(line.timeMs)
                            }.padding(horizontal = 24.dp, vertical = 10.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onAdjustLyrics(-500) }) { Text("−0.5 s", color = amber) }
                    TextButton(onClick = { showLyricsEdit = true }) { Text("Editar .lrc", color = amber) }
                    TextButton(onClick = { onAdjustLyrics(500) }) { Text("+0.5 s", color = amber) }
                }
                TextButton(onClick = onRemoveLyrics, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text("Quitar letras importadas", color = muted)
                }
            }
            Spacer(Modifier.height(25.dp))
        }
    }
    if (showLyricsEdit) {
        var editing by remember(state.song?.id) {
            mutableStateOf(lyrics.joinToString("\n") { entry ->
                if (entry.timeMs < 0L) entry.text else {
                    val ms = entry.timeMs
                    "[%02d:%02d.%02d] %s".format(ms / 60000, (ms / 1000) % 60, (ms % 1000) / 10, entry.text)
                }
            })
        }
        AlertDialog(onDismissRequest = { showLyricsEdit = false },
            title = { Text("Editar letra local") },
            text = { OutlinedTextField(editing, { editing = it.take(20000) },
                label = { Text("Líneas LRC [mm:ss.xx]") }, maxLines = 14, minLines = 8) },
            confirmButton = { TextButton(onClick = { onEditLyrics(editing); showLyricsEdit = false }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { showLyricsEdit = false }) { Text("Cancelar") } }
        )
    }
    if (showTimer) {
        AlertDialog(onDismissRequest = { showTimer = false },
            title = { Text("Temporizador de apagado") },
            text = { Column {
                Text(if (sleepMinutes > 0) "Activo: $sleepMinutes minutos" else "Sin temporizador")
                listOf(0, 15, 30, 45, 60, 90).forEach { minutes ->
                    Text(if (minutes == 0) "Desactivar" else "$minutes minutos", color = amber,
                        modifier = Modifier.fillMaxWidth().clickable {
                            onSleepTimer(minutes); showTimer = false
                        }.padding(10.dp))
                }
            } },
            confirmButton = { TextButton(onClick = { showTimer = false }) { Text("Cerrar") } })
    }
    if (showEdit && song != null) {
        var newTitle by remember(song.id) { mutableStateOf(song.title) }
        var newArtist by remember(song.id) { mutableStateOf(song.artistDisplay) }
        var newAlbum by remember(song.id) { mutableStateOf(song.albumTitle) }
        AlertDialog(onDismissRequest = { showEdit = false },
            title = { Text("Editar datos de AmberMusic") },
            text = { Column {
                Text("No modifica el MP3/FLAC original.", fontSize = 12.sp)
                OutlinedTextField(newTitle, { newTitle = it.take(140) }, label = { Text("Título") }, singleLine = true)
                OutlinedTextField(newArtist, { newArtist = it.take(240) }, label = { Text("Artistas") }, singleLine = true)
                OutlinedTextField(newAlbum, { newAlbum = it.take(140) }, label = { Text("Álbum") }, singleLine = true)
            } },
            confirmButton = { TextButton(onClick = {
                onMetadataEdit(song.id, newTitle, newArtist, newAlbum)
                showEdit = false
            }, enabled = newTitle.isNotBlank() && newArtist.isNotBlank() && newAlbum.isNotBlank()) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { showEdit = false }) { Text("Cancelar") } })
    }
    if (showRoute) {
        AlertDialog(
            onDismissRequest = { showRoute = false },
            containerColor = Color(0xFF101E2E),
            title = { Text("Salida de audio", color = Color.White) },
            text = {
                Text(
                    "Ruta detectada: ${state.route.name}. " +
                        if (state.route.routeConfirmed) "El sistema indica esta ruta para multimedia."
                        else "En Android 12 esta información puede ser aproximada.",
                    color = muted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRoute = false
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                }) { Text("Bluetooth", color = amber) }
            },
            dismissButton = {
                TextButton(onClick = { showRoute = false }) { Text("Cerrar", color = Color.White) }
            }
        )
    }
}


@Composable
private fun PlayerShortcut(title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(18.dp))
        .background(LocalAmberPalette.current.surfaceRaised)
        .clickable(onClick = onClick)
        .padding(horizontal = 7.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center) {
        Text(title, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CircleAction(onClick: () -> Unit, description: String, content: @Composable () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(glass).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
fun ArtistBubblesRow(artists: List<ArtistEntity>, initials: List<String>, onClick: () -> Unit) {
    val count = minOf(artists.size, 2) + if (artists.size > 2) 1 else 0
    Row(
        Modifier.width((if (count == 0) 0 else 57 + (count - 1) * 46).dp)
            .height(56.dp).clickable(enabled = artists.isNotEmpty(), onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val labels = initials.take(2) + if (artists.size > 2) listOf("+${artists.size - 2}") else emptyList()
        labels.forEachIndexed { index, label ->
            Box(
                Modifier.offset(x = (-11 * index).dp).zIndex((labels.size - index).toFloat())
                    .size(57.dp).clip(CircleShape).background(glass),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun PillSeekbar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragProgress by remember(durationMs) { mutableStateOf<Float?>(null) }
    val fraction = dragProgress ?: if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else 0f
    Canvas(
        Modifier.fillMaxWidth().height(32.dp)
            .pointerInput(durationMs) {
                detectTapGestures { position ->
                    if (durationMs > 0) onSeek((position.x / size.width).coerceIn(0f, 1f).times(durationMs).roundToLong())
                }
            }
            .pointerInput(durationMs) {
                detectDragGestures(
                    onDragStart = { point -> dragProgress = (point.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        dragProgress?.let { onSeek((it * durationMs).roundToLong()) }
                        dragProgress = null
                    },
                    onDragCancel = { dragProgress = null },
                    onDrag = { change, _ ->
                        change.consume()
                        dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                )
            }
    ) {
        val middle = size.height / 2f
        val track = 4.dp.toPx()
        drawRoundRect(Color(0xFF2A3D52), Offset(0f, middle - track / 2),
            Size(size.width, track), androidx.compose.ui.geometry.CornerRadius(track))
        drawRoundRect(Color.White, Offset(0f, middle - track / 2),
            Size(size.width * fraction, track), androidx.compose.ui.geometry.CornerRadius(track))
        val thumbWidth = 13.dp.toPx()
        val thumbHeight = 25.dp.toPx()
        drawRoundRect(Color.White,
            Offset((size.width * fraction - thumbWidth / 2).coerceIn(0f, (size.width - thumbWidth).coerceAtLeast(0f)),
                middle - thumbHeight / 2),
            Size(thumbWidth, thumbHeight), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
    }
}

private fun formatTime(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0) / 1000)
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%02d:%02d".format(seconds / 60, seconds % 60)
}
