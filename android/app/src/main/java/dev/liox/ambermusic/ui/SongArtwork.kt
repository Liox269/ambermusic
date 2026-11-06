package dev.liox.ambermusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.liox.ambermusic.artwork.ArtworkRepository
import dev.liox.ambermusic.data.local.SongEntity
import org.koin.compose.koinInject

/** Embedded artwork is extracted lazily for visible library rows, off the UI thread. */
@Composable
fun SongArtwork(
    song: SongEntity?,
    size: Dp = 52.dp,
    modifier: Modifier = Modifier
) {
    val repository: ArtworkRepository = koinInject()
    val coverVersion by repository.version.collectAsState()
    val artwork by produceState<java.io.File?>(null, song?.id, song?.modifiedAtSeconds, song?.albumId, coverVersion) {
        value = if (song == null) null else repository.fileFor(song)
    }
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(7.dp))
            .background(Color(0xFF0D2032)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.MusicNote,
            contentDescription = null,
            tint = Color(0xFF739FBD),
            modifier = Modifier.size(size * 0.48f)
        )
        if (artwork != null) {
            AsyncImage(
                model = artwork,
                contentDescription = song?.let { "Carátula de ${it.title}" },
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}
