package dev.liox.ambermusic.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.liox.ambermusic.artwork.ArtworkRepository
import dev.liox.ambermusic.data.local.SongEntity

/** Only three covers are composed. Neighbor covers are loaded off the UI thread. */
@Composable
fun CoverCarousel(
    song: SongEntity?,
    currentCover: Any?,
    queue: List<SongEntity>,
    artwork: ArtworkRepository,
    fitCover: Boolean,
    reduceMotion: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    // Keep the previous image visible while the next cover loads from disk.
    var retainedCover by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(song?.id, currentCover) {
        if (song == null) retainedCover = null
        else if (currentCover != null) retainedCover = currentCover
    }
    val presentedCover = currentCover ?: retainedCover
    val index = queue.indexOfFirst { it.id == song?.id }
    var lastIndex by remember { mutableStateOf<Int?>(null) }
    var forward by remember { mutableStateOf(true) }
    LaunchedEffect(song?.id, index) {
        val last = lastIndex
        if (last != null && last != index) forward = index >= last
        if (index >= 0) lastIndex = index
    }
    val previous = queue.getOrNull(index - 1)
    val next = if (index >= 0) queue.getOrNull(index + 1) else null
    val previousCover by produceState<java.io.File?>(null, previous?.id, previous?.modifiedAtSeconds) {
        value = previous?.let { artwork.fileFor(it) }
    }
    val nextCover by produceState<java.io.File?>(null, next?.id, next?.modifiedAtSeconds) {
        value = next?.let { artwork.fileFor(it) }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1.02f)) {
        val side = maxWidth * 0.39f
        val main = maxWidth * 0.96f
        val corner = RoundedCornerShape(LocalAmberCorners.current)
        if (previousCover != null) {
            AsyncImage(
                model = previousCover, contentDescription = "Anterior: ${previous?.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.CenterStart)
                    .offset(x = -side * 0.52f).size(side).clip(corner).clickable { onPrevious() }
            )
        }
        if (nextCover != null) {
            AsyncImage(
                model = nextCover, contentDescription = "Siguiente: ${next?.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.CenterEnd)
                    .offset(x = side * 0.52f).size(side).clip(corner).clickable { onNext() }
            )
        }
        Box(
            Modifier.align(Alignment.Center).size(main).clip(corner)
                .background(LocalAmberPalette.current.background)
                .pointerInput(song?.id) {
                    var distance = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { distance = 0f },
                        onDragEnd = {
                            if (distance < -65.dp.toPx()) onNext()
                            else if (distance > 65.dp.toPx()) onPrevious()
                        },
                        onHorizontalDrag = { change, amount -> change.consume(); distance += amount }
                    )
                }, contentAlignment = Alignment.Center
        ) {
            // Fill the full artwork surface; no flat gray letterbox behind wide covers.
            if (presentedCover != null && fitCover) {
                AsyncImage(model = presentedCover, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().alpha(0.38f))
            }
            AnimatedContent(
                targetState = presentedCover,
                contentKey = { it?.toString() },
                transitionSpec = {
                    val duration = if (reduceMotion) 0 else 250
                    (slideInHorizontally(
                        initialOffsetX = { width -> if (forward) width else -width },
                        animationSpec = tween(duration, easing = FastOutSlowInEasing)) +
                        fadeIn(tween(duration))) togetherWith
                        (slideOutHorizontally(
                            targetOffsetX = { width -> if (forward) -width else width },
                            animationSpec = tween(duration, easing = FastOutSlowInEasing)) +
                        fadeOut(tween(duration)))
                },
                label = "Cambio de portada"
            ) { cover ->
                if (cover != null) AsyncImage(
                    model = cover, contentDescription = "Portada de ${song?.title.orEmpty()}",
                    contentScale = if (fitCover) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                ) else Icon(Icons.Filled.MusicNote, contentDescription = null,
                    tint = Color.LightGray, modifier = Modifier.size(62.dp))
            }
        }
    }
}
