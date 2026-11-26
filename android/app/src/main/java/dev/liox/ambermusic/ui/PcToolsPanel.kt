package dev.liox.ambermusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.liox.ambermusic.download.DownloadAudioFormat
import dev.liox.ambermusic.viewmodel.LibraryUiState
import dev.liox.ambermusic.viewmodel.MusicViewModel

@Composable
fun PcToolsPanel(
    library: LibraryUiState,
    viewModel: MusicViewModel
) {
    val player by viewModel.playerUiState.collectAsState()
    val urlDownloading by viewModel.urlDownloading.collectAsState()
    val urlProgress by viewModel.urlDownloadProgress.collectAsState()
    val urlStatus by viewModel.urlDownloadStatus.collectAsState()

    val bulkRunning by viewModel.bulkSyncRunning.collectAsState()
    val bulkProgress by viewModel.bulkSyncProgress.collectAsState()
    val bulkStatus by viewModel.bulkSyncStatus.collectAsState()

    val hdCandidates by viewModel.hdCoverCandidates.collectAsState()
    val hdSearching by viewModel.hdCoverSearching.collectAsState()

    var urlInput by rememberSaveable { mutableStateOf("") }
    var selectedFormat by rememberSaveable { mutableStateOf(DownloadAudioFormat.FLAC) }
    var coverQuery by rememberSaveable(player.song?.id) {
        mutableStateOf(
            player.song?.let { "${it.artistDisplay} ${it.title}".trim() }.orEmpty()
        )
    }
    var coverNotice by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Descargar por URL", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("YouTube, Spotify, Apple, TIDAL, Deezer o Artista - Canción") },
                    enabled = !urlDownloading
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DownloadAudioFormat.entries.forEach { fmt ->
                        FilterChip(
                            selected = selectedFormat == fmt,
                            onClick = { selectedFormat = fmt },
                            label = { Text(fmt.label) },
                            enabled = !urlDownloading
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = { viewModel.downloadFromUrl(urlInput, selectedFormat) },
                        enabled = !urlDownloading && urlInput.isNotBlank()
                    ) {
                        Text(if (urlDownloading) "Descargando…" else "Descargar ${selectedFormat.label}")
                    }
                }
                if (urlDownloading) {
                    LinearProgressIndicator(
                        progress = { urlProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (!urlStatus.isNullOrBlank()) {
                    Text(urlStatus.orEmpty(), fontSize = 12.sp, color = Color.White.copy(alpha = 0.78f))
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Portadas HD y letras (${library.songs.size} pistas)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.syncAllCoversAndLyrics(overwriteExistingCovers = false) },
                        enabled = !bulkRunning && library.songs.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Completar faltantes", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = { viewModel.syncAllCoversAndLyrics(overwriteExistingCovers = true) },
                        enabled = !bulkRunning && library.songs.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Todas a HD", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (bulkRunning) {
                    LinearProgressIndicator(
                        progress = { bulkProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { viewModel.cancelBulkSync() }) {
                        Text("Cancelar sincronización")
                    }
                }
                if (!bulkStatus.isNullOrBlank()) {
                    Text(bulkStatus.orEmpty(), fontSize = 12.sp, color = Color.White.copy(alpha = 0.78f))
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ImageSearch, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Buscador de portadas HD", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = coverQuery,
                        onValueChange = { coverQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Artista y canción") }
                    )
                    Button(
                        onClick = { viewModel.searchHdCovers(coverQuery) },
                        enabled = !hdSearching && coverQuery.isNotBlank()
                    ) {
                        Text(if (hdSearching) "…" else "Buscar")
                    }
                }
                if (!coverNotice.isNullOrBlank()) {
                    Text(coverNotice.orEmpty(), fontSize = 12.sp, color = Color.White.copy(alpha = 0.78f))
                }
            }
        }

        items(hdCandidates, key = { it.hdUrl }) { candidate ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .clickable {
                        viewModel.applyHdCoverToCurrent(candidate) { msg -> coverNotice = msg }
                    }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = candidate.previewUrl,
                    contentDescription = candidate.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(candidate.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(
                        "${candidate.source} · ${candidate.resolution}",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }
                Text("Aplicar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
        }

        item { Spacer(Modifier.height(100.dp)) }
    }
}
