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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.Dp
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
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    coverSize: Dp
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
    // This component occupies exactly the visible artwork dimensions. Never place a
    // small source preview in a large empty panel. Side margins remain system-gesture-safe.
    Box(
        Modifier.fillMaxWidth().height(coverSize),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.size(coverSize).clip(RoundedCornerShape(22.dp))
                .background(LocalAmberPalette.current.surfaceRaised)
                .pointerInput(song?.id, onPrevious, onNext, onDismiss) {
                    var dragX = 0f
                    var dragY = 0f
                    detectDragGestures(
                        onDragStart = { dragX = 0f; dragY = 0f },
                        onDrag = { change, drag ->
                            change.consume()
                            dragX += drag.x
                            dragY += drag.y
                        },
                        onDragEnd = {
                            when (PlayerGestureLogic.coverGesture(dragX, dragY, 60.dp.toPx())) {
                                PlayerGestureLogic.ArtworkGesture.PREVIOUS -> onPrevious()
                                PlayerGestureLogic.ArtworkGesture.NEXT -> onNext()
                                PlayerGestureLogic.ArtworkGesture.LIBRARY -> onDismiss()
                                PlayerGestureLogic.ArtworkGesture.NONE -> Unit
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = song?.id to presentedCover,
                contentKey = { it.first },
                transitionSpec = {
                    val duration = if (reduceMotion) 0 else 280
                    (fadeIn(tween(duration, easing = FastOutSlowInEasing))) togetherWith
                        fadeOut(tween(duration, easing = FastOutSlowInEasing))
                },
                label = "Transición fluida de portada"
            ) { (_, cover) ->
                if (cover != null) {
                    AsyncImage(
                        model = cover,
                        contentDescription = "Portada de ${song?.title.orEmpty()}",
                        contentScale = if (fitCover) ContentScale.Fit else ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                } else {
                    Icon(Icons.Filled.MusicNote, contentDescription = "Portada no disponible",
                        tint = LocalAmberPalette.current.muted, modifier = Modifier.size(54.dp))
                }
            }
        }
    }

}
