package dev.liox.ambermusic.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.liox.ambermusic.data.local.SongEntity
import dev.liox.ambermusic.lyrics.LyricLine
import dev.liox.ambermusic.lyrics.LyricSyncPolicy
import java.io.File

/**
 * A dedicated full-screen lyrics destination, never a bottom sheet.
 * Every lyric transition uses Media3's actual reported position and works after pause/seek.
 * Animated scroll triggers only when the active timestamp changes, not on each position tick.
 */
@Composable
internal fun ImmersiveLyricsScreen(
    song: SongEntity?,
    artwork: Any?,
    ambientArtwork: File?,
    lyrics: List<LyricLine>,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    reduceMotion: Boolean,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onImport: () -> Unit,
    onSearchLrclib: () -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onClear: () -> Unit
) {
    val accent = LocalAmberAccent.current
    val foreground = LocalAmberPalette.current.foreground
    val muted = LocalAmberPalette.current.muted
    val hasTimestamps = remember(lyrics) { lyrics.any { it.timeMs >= 0L } }
    val activeIndex = LyricSyncPolicy.activeIndex(lyrics, positionMs)
    val currentTitle = song?.title ?: "Sin reproducción"
    var showTools by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Prefer cached album blur: this runs off the main thread and is reused.
        // The local cover acts as a soft-blurred fallback while the cache is being prepared.
        Crossfade(
            targetState = ambientArtwork ?: artwork,
            animationSpec = tween(if (reduceMotion) 0 else 360, easing = FastOutSlowInEasing),
            label = "Fondo de letras de la carátula"
        ) { source ->
            if (source != null) {
                val cached = source == ambientArtwork
                AsyncImage(
                    model = source,
                    contentDescription = null,
                    contentScale = if (cached) ContentScale.FillBounds else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                        .then(if (cached) Modifier.alpha(0.68f) else Modifier.blur(58.dp).alpha(0.61f))
                )
            }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.52f),
            0.30f to Color.Black.copy(alpha = 0.46f),
            0.66f to Color.Black.copy(alpha = 0.56f),
            1f to Color.Black.copy(alpha = 0.91f)
        )))

        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(45.dp)) {
                    Icon(Icons.Filled.ArrowBack, "Volver al reproductor",
                        tint = Color.White, modifier = Modifier.size(24.dp))
                }
                AsyncImage(
                    model = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(currentTitle, color = Color.White, fontSize = 16.sp,
                        fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song?.artistDisplay.orEmpty(), color = Color.White.copy(alpha = .70f),
                        fontSize = 12.sp, fontFamily = FontFamily.SansSerif,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box {
                    IconButton(onClick = { showTools = true }) {
                        Icon(Icons.Filled.MoreVert, "Opciones de letras", tint = Color.White)
                    }
                    DropdownMenu(expanded = showTools, onDismissRequest = { showTools = false }) {
                        DropdownMenuItem(
                            text = { Text("Importar / Reemplazar .lrc") },
                            onClick = { showTools = false; onImport() }
                        )
                        DropdownMenuItem(
                            text = { Text("Buscar en LRCLIB") },
                            onClick = { showTools = false; onSearchLrclib() }
                        )
                        if (lyrics.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("Adelantar letras 250 ms") },
                                onClick = { showTools = false; onAdjustOffset(-250L) }
                            )
                            DropdownMenuItem(
                                text = { Text("Retrasar letras 250 ms") },
                                onClick = { showTools = false; onAdjustOffset(250L) }
                            )
                            DropdownMenuItem(
                                text = { Text("Borrar letras") },
                                onClick = { showTools = false; onClear() }
                            )
                        }
                    }
                }
            }

            if (lyrics.isEmpty()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Aún no hay letras", color = Color.White, fontWeight = FontWeight.Bold,
                        fontSize = 26.sp, fontFamily = FontFamily.SansSerif)
                    Spacer(Modifier.height(12.dp))
                    Text("Importa un archivo LRC o busca letras para esta canción.",
                        color = Color.White.copy(alpha = .64f),
                        fontSize = 14.sp, fontFamily = FontFamily.SansSerif)
                    Spacer(Modifier.height(15.dp))
                    TextButton(onClick = onSearchLrclib) {
                        Text("Buscar letras", color = accent, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onImport) {
                        Text("Importar archivo .lrc", color = foreground)
                    }
                }
            } else {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val anchorPadding = (maxHeight * 0.37f).coerceAtLeast(70.dp)
                    val density = LocalDensity.current
                    val offsetPx = with(density) { (maxHeight * 0.21f).roundToPx() }
                    val scroll = rememberLazyListState()
                    LaunchedEffect(song?.id, activeIndex, reduceMotion, lyrics) {
                        // Keep the active line near the center and avoid a recomposition-driven jump.
                        if (hasTimestamps && activeIndex >= 0) {
                            val target = activeIndex + 1 // First item is top centering spacer.
                            if (reduceMotion) scroll.scrollToItem(target, scrollOffset = -offsetPx)
                            else scroll.animateScrollToItem(target, scrollOffset = -offsetPx)
                        } else if (!hasTimestamps) {
                            scroll.scrollToItem(0)
                        }
                    }
                    LazyColumn(
                        state = scroll,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 23.dp),
                        verticalArrangement = Arrangement.spacedBy(13.dp)
                    ) {
                        item(key = "lyric-leading") { Spacer(Modifier.height(anchorPadding)) }
                        itemsIndexed(lyrics, key = { i, _ -> "lyric-$i" }) { index, line ->
                            val isActive = hasTimestamps && index == activeIndex
                            val isPrevious = hasTimestamps && index < activeIndex
                            val lineColor by animateColorAsState(
                                targetValue = if (isActive) accent
                                    else Color.White.copy(alpha = if (isPrevious) 0.42f else 0.69f),
                                animationSpec = tween(if (reduceMotion) 0 else 280),
                                label = "Color de línea sincronizada"
                            )
                            val size by animateFloatAsState(
                                targetValue = if (isActive) 23f else 19f,
                                animationSpec = tween(if (reduceMotion) 0 else 280),
                                label = "Tamaño de línea sincronizada"
                            )
                            Text(
                                line.text,
                                color = lineColor,
                                fontSize = size.sp,
                                lineHeight = (size * 1.35f).sp,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = FontFamily.SansSerif,
                                modifier = Modifier.fillMaxWidth()
                                    .clickable(enabled = line.timeMs >= 0L) { onSeek(line.timeMs) }
                                    .padding(vertical = 7.dp)
                            )
                        }
                        item(key = "lyric-trailing") { Spacer(Modifier.height(anchorPadding)) }
                    }
                }
            }

            // Lightweight persistent transport: no heavy card or large bottom empty area.
            Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 14.dp)) {
                val fraction = if (durationMs > 0L)
                    (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    else 0f
                val animatedFraction by animateFloatAsState(
                    targetValue = fraction,
                    animationSpec = tween(if (reduceMotion) 0 else 220),
                    label = "Progreso de letras"
                )
                Box(
                    Modifier.fillMaxWidth().height(24.dp)
                        .clickable(enabled = durationMs > 0L) {
                            // Tap in the lyric text is the seek interaction; this bar is visual.
                            // Player seek remains available via the main Now Playing view.
                        },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape)
                        .background(Color.White.copy(alpha = .22f)))
                    Box(Modifier.fillMaxWidth(animatedFraction).height(3.dp).clip(CircleShape)
                        .background(accent))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(lyricTime(positionMs), color = muted, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    Text(if (hasTimestamps) "Letras sincronizadas" else "Letras sin sincronizar",
                        color = muted, fontSize = 10.sp)
                    Spacer(Modifier.weight(1f))
                    Text(lyricTime(durationMs), color = muted, fontSize = 11.sp)
                    Spacer(Modifier.width(7.dp))
                    IconButton(onClick = onPlayPause) {
                        Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (isPlaying) "Pausar" else "Reproducir", tint = accent,
                            modifier = Modifier.size(27.dp))
                    }
                }
            }
        }
    }
}

private fun lyricTime(millis: Long): String {
    val seconds = (millis.coerceAtLeast(0) / 1000L)
    val min = seconds / 60L
    val sec = seconds % 60L
    return "%d:%02d".format(min, sec)
}
