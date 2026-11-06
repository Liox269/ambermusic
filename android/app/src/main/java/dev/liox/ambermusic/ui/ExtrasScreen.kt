package dev.liox.ambermusic.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Headphones
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.liox.ambermusic.data.local.SongEntity
import dev.liox.ambermusic.smart.SmartPlaylist
import dev.liox.ambermusic.smart.SmartRules
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import dev.liox.ambermusic.viewmodel.LibraryUiState
import dev.liox.ambermusic.viewmodel.MusicViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ExtrasScreen(
    library: LibraryUiState,
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onPlayer: () -> Unit,
    startOnSettings: Boolean = false
) {
    val accent = LocalAmberAccent.current
    var tab by rememberSaveable(startOnSettings) { mutableIntStateOf(if (startOnSettings) 4 else 0) }
    BackHandler(enabled = tab in 5..9) { tab = 4 }
    var playlistId by rememberSaveable { mutableStateOf<Long?>(null) }
    var createDialog by remember { mutableStateOf(false) }
    var showFolders by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Long?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val theme by viewModel.theme.collectAsState()
    val offload by viewModel.offload.collectAsState()
    val gain by viewModel.replayGain.collectAsState()
    val soft by viewModel.smoothTransition.collectAsState()
    val immersive by viewModel.immersive.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val text = viewModel.exportBackup()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) }
                        ?: error("No se pudo abrir el destino")
                }
            }.onSuccess { info = "Respaldo guardado" }
                .onFailure { info = it.localizedMessage ?: "Error al exportar" }
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("No se pudo abrir el archivo")
                }
                viewModel.importBackup(text)
            }.onSuccess { info = "Se restauraron $it asociaciones a canciones presentes en el equipo" }
                .onFailure { info = it.localizedMessage ?: "Error al importar" }
        }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (tab in 5..9) tab = 4 else onBack() }) { Icon(Icons.Filled.ArrowBack, "Volver", tint = Color.White) }
            Text(when (tab) { 4 -> "Ajustes"; 5 -> "Duplicados"; 6 -> "Biblioteca y carpetas"; 7 -> "Apariencia"; 8 -> "Inmersión visual"; 9 -> "Audio"; else -> "Mi AmberMusic" }, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))

        }
        if (tab in 0..3) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Playlists", "Estadísticas", "Inteligentes", "Duplicados").forEachIndexed { index, name ->
                FilterChip(selected = index == tab, onClick = { tab = index; playlistId = null }, label = { Text(name) })
            }
        }
        when (tab) {
            0 -> {
                val currentId = playlistId
                if (currentId == null) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Mis playlists", color = Color.White, modifier = Modifier.weight(1f))
                        TextButton(onClick = { createDialog = true }) { Text("+ Nueva", color = accent) }
                    }
                    LazyColumn {
                        if (library.playlists.isEmpty()) item {
                            Text("Aún no tienes playlists. Pulsa + Nueva para crear la primera.",
                                color = Color.LightGray, modifier = Modifier.padding(20.dp))
                        }
                        items(library.playlists, key = { it.id }) { playlist ->
                            Row(Modifier.fillMaxWidth().clickable { playlistId = playlist.id }
                                .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.MusicNote, null, tint = accent)
                                Text(playlist.name, color = Color.White, modifier = Modifier.weight(1f).padding(start = 14.dp))
                                IconButton(onClick = { pendingDelete = playlist.id }) {
                                    Icon(Icons.Filled.Delete, "Eliminar playlist", tint = Color.LightGray)
                                }
                            }
                        }
                    }
                } else {
                    val playlist = library.playlists.firstOrNull { it.id == currentId }
                    val songs by remember(currentId) { viewModel.observePlaylistSongs(currentId) }.collectAsState(emptyList())
                    TextButton(onClick = { playlistId = null }) { Text("‹ ${playlist?.name ?: "Playlist"}", color = accent) }
                    SongListBlock(songs,
                        onClick = { id -> viewModel.playFromPlaylist(currentId, id); onPlayer() },
                        onRemove = { id -> viewModel.removeFromPlaylist(id, currentId) })
                }
            }
            1 -> LazyColumn {
                item {
                    SectionName("Resumen de escucha")
                    Text("Últimos 7 días: ${library.weekPlays} reproducciones · " +
                        "Últimos 30 días: ${library.monthPlays}",
                        color = Color.White, modifier = Modifier.padding(horizontal = 16.dp))
                    SectionName("Tus favoritas · ${library.favoriteSongs.size}")
                }
                items(library.favoriteSongs.take(12), key = { "favorite_${it.id}" }) { song ->
                    CompactTrack(song) { viewModel.playSelection(library.favoriteSongs, song.id); onPlayer() }
                }
                item { SectionName("Más escuchadas") }
                items(library.mostPlayed.take(15), key = { "most_${it.id}" }) { song ->
                    CompactTrack(song) { viewModel.playSelection(library.mostPlayed, song.id); onPlayer() }
                }
                item { SectionName("Top de la semana") }
                items(library.weeklyTop.take(8), key = { "week_${it.id}" }) { song ->
                    CompactTrack(song) { viewModel.playSelection(library.weeklyTop, song.id); onPlayer() }
                }
                item { SectionName("Top del mes") }
                items(library.monthlyTop.take(8), key = { "month_${it.id}" }) { song ->
                    CompactTrack(song) { viewModel.playSelection(library.monthlyTop, song.id); onPlayer() }
                }
                item { SectionName("Escuchadas recientemente") }
                items(library.recentlyPlayed.take(10), key = { "recent_${it.id}" }) { song ->
                    CompactTrack(song) { viewModel.playSelection(library.recentlyPlayed, song.id); onPlayer() }
                }
                item { Spacer(Modifier.height(100.dp)) }
            }
            2 -> SmartPlaylistsPanel(library, viewModel, onPlayer)
            3 -> DuplicatesPanel(library, viewModel)
            5 -> DuplicatesPanel(library, viewModel)
            4 -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("Personaliza tu música sin menús interminables", color = Color.LightGray, fontSize = 13.sp, modifier = Modifier.padding(vertical = 18.dp)) }
                item { SettingsEntry("Biblioteca y carpetas", "Carpetas permitidas y actualizar música", Icons.Filled.FolderOpen, accent) { tab = 6 } }
                item { SettingsEntry("Duplicados", "Analizar, ocultar y mostrar copias", Icons.Filled.ContentCopy, accent) { tab = 5 } }
                item { SettingsEntry("Apariencia", "Elige entre los temas AMOLED", Icons.Filled.Palette, accent) { tab = 7 } }
                item { SettingsEntry("Inmersión visual", "Fondo ambiental y blur", Icons.Filled.BlurOn, accent) { tab = 8 } }
                item { SettingsEntry("Audio", "Offload, ReplayGain y transiciones", Icons.Filled.Headphones, accent) { tab = 9 } }
                item { Spacer(Modifier.height(85.dp)) }
            }
            6 -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                item {
                    SectionName("Carpetas de música")
                    Text("Selecciona las carpetas reconocidas por Android. También se incluyen sus subcarpetas y solo canciones de más de un minuto.", color = Color.LightGray, fontSize = 13.sp)
                    TextButton(onClick = { viewModel.loadAvailableFolders(); showFolders = true }) { Text("Elegir carpetas", color = accent) }
                    TextButton(onClick = { viewModel.scanLocalMusic() }) { Text("Actualizar biblioteca", color = accent) }
                }
            }
            7 -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                item {
                    SectionName("Temas AMOLED")
                    Text("El tema cambia los controles sin alterar los colores de la portada.", color = Color.LightGray, fontSize = 13.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.padding(top = 16.dp)) {
                        AmberPalettes.all.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                row.forEach { palette ->
                                    Row(Modifier.weight(1f).clip(RoundedCornerShape(15.dp))
                                        .background(if (theme == palette.id) palette.surfaceRaised else palette.surface)
                                        .clickable { viewModel.setTheme(palette.id) }.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(23.dp).clip(RoundedCornerShape(10.dp)).background(palette.accent))
                                        Text(palette.title, color = Color.White, fontSize = 11.sp, maxLines = 1,
                                            modifier = Modifier.padding(start = 8.dp))
                                    }
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            8 -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                item {
                    SectionName("Fondo ambiental")
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Blur dinámico de la portada", color = Color.White, modifier = Modifier.weight(1f))
                        Switch(checked = immersive, onCheckedChange = viewModel::setImmersive)
                    }
                    Text("Se genera por portada y se reutiliza desde caché. No se recalcula en cada fotograma.", color = Color.Gray, fontSize = 13.sp)
                }
            }
            9 -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                item {
                    SectionName("Reproducción")
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Audio Offload", color = Color.White)
                            Text("Menor consumo si el dispositivo y el audio son compatibles", color = Color.Gray, fontSize = 12.sp)
                        }
                        Switch(checked = offload, onCheckedChange = viewModel::setOffload)
                    }
                    SectionName("ReplayGain")
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("off" to "Desactivado", "track" to "Por pista", "album" to "Por álbum").forEach { (value, title) ->
                            FilterChip(selected = gain == value, onClick = { viewModel.setReplayGain(value) }, label = { Text(title) })
                        }
                    }
                    Text("Disponible solo cuando el archivo contiene etiquetas ReplayGain compatibles.", color = Color.Gray, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Inicio suave de pista", color = Color.White)
                            Text("Fundido de entrada de 350 ms", color = Color.Gray, fontSize = 12.sp)
                        }
                        Switch(checked = soft, onCheckedChange = viewModel::setSmoothTransitions)
                    }
                }
            }
        }
    }
    pendingDelete?.let { chosenId ->
        AlertDialog(onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar playlist") },
            text = { Text("Se eliminará la lista, no sus canciones del teléfono.") },
            confirmButton = { TextButton(onClick = {
                viewModel.deletePlaylist(chosenId)
                pendingDelete = null
            }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } })
    }
    if (createDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { createDialog = false },
            title = { Text("Nueva playlist") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text("Nombre") }) },
            confirmButton = { TextButton(onClick = { viewModel.createPlaylist(name); createDialog = false }, enabled = name.isNotBlank()) { Text("Crear") } },
            dismissButton = { TextButton(onClick = { createDialog = false }) { Text("Cancelar") } })
    }
    info?.let { message ->
        AlertDialog(onDismissRequest = { info = null }, title = { Text("AmberMusic") }, text = { Text(message) },
            confirmButton = { TextButton(onClick = { info = null }) { Text("Entendido") } })
    }
    if (showFolders) {
        val chosen by viewModel.selectedFolders.collectAsState()
        val candidates by viewModel.availableFolders.collectAsState()
        val loading by viewModel.folderLoading.collectAsState()
        MusicFoldersDialog(
            current = chosen, available = candidates, loading = loading,
            onSave = { selected -> viewModel.updateFolderSelection(selected); showFolders = false },
            onDismiss = { showFolders = false }
        )
    }

}

@Composable
private fun SmartPlaylistsPanel(library: LibraryUiState, viewModel: MusicViewModel,
    onPlayer: () -> Unit) {
    val rules by viewModel.smartPlaylists.collectAsState()
    var showingCreate by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = rules.firstOrNull { it.id == selectedId }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Playlists automáticas", color = Color.White, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            TextButton(onClick = { showingCreate = true }) { Text("+ Nueva") }
        }
        Text("Se actualizan solas al añadir música. No duplican archivos.",
            color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        if (selected == null) {
            LazyColumn {
                if (rules.isEmpty()) item {
                    Text("Sin listas inteligentes. Crea una regla para agrupar canciones automáticamente.",
                        color = Color.LightGray, modifier = Modifier.padding(20.dp))
                }
                items(rules, key = { it.id }) { rule ->
                    val amount = viewModel.smartSongs(rule, library).size
                    Row(Modifier.fillMaxWidth().clickable { selectedId = rule.id }
                        .padding(horizontal = 17.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MusicNote, null, tint = LocalAmberAccent.current)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(rule.name, color = Color.White)
                            Text("${rule.type} · $amount canciones", color = Color.Gray, fontSize = 12.sp)
                        }
                        IconButton(onClick = { viewModel.removeSmartPlaylist(rule.id) }) {
                            Icon(Icons.Filled.Delete, "Eliminar regla", tint = Color.LightGray)
                        }
                    }
                }
            }
        } else {
            val songs = viewModel.smartSongs(selected, library)
            TextButton(onClick = { selectedId = null }) { Text("‹ ${selected.name} (${songs.size})") }
            LazyColumn {
                items(songs, key = { it.id }) { song ->
                    CompactTrack(song) { viewModel.playFromSmartList(selected, library, song.id); onPlayer() }
                }
            }
        }
    }
    if (showingCreate) {
        var name by remember { mutableStateOf("") }
        var value by remember { mutableStateOf("") }
        var type by remember { mutableStateOf(SmartRules.ARTIST) }
        var expanded by remember { mutableStateOf(false) }
        val choices = listOf(
            SmartRules.ARTIST to "Artista", SmartRules.ALBUM to "Álbum",
            SmartRules.GENRE to "Género", SmartRules.FOLDER to "Carpeta",
            SmartRules.FORMAT to "Formato", SmartRules.FAVOURITES to "Favoritas",
            SmartRules.POPULAR to "Muy escuchadas", SmartRules.RECENT to "Últimos 30 días"
        )
        AlertDialog(onDismissRequest = { showingCreate = false }, title = { Text("Nueva playlist automática") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it.take(80) }, label = { Text("Nombre") }, singleLine = true)
                    TextButton(onClick = { expanded = true }) {
                        Text("Regla: ${choices.first { it.first == type }.second} ▾")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        choices.forEach { (key, title) -> DropdownMenuItem(
                            text = { Text(title) }, onClick = { type = key; expanded = false }) }
                    }
                    if (type in listOf(SmartRules.ARTIST, SmartRules.ALBUM, SmartRules.GENRE,
                            SmartRules.FOLDER, SmartRules.FORMAT, SmartRules.POPULAR)) {
                        OutlinedTextField(value, { value = it.take(100) }, singleLine = true,
                            label = { Text(if (type == SmartRules.POPULAR) "Mínimo de reproducciones" else "Contiene") })
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                viewModel.createSmartPlaylist(name, type, value); showingCreate = false
            }, enabled = name.isNotBlank() && (type in listOf(SmartRules.FAVOURITES,
                SmartRules.RECENT) || value.isNotBlank())) { Text("Crear") } },
            dismissButton = { TextButton(onClick = { showingCreate = false }) { Text("Cancelar") } })
    }
}

@Composable
private fun DuplicatesPanel(library: LibraryUiState, viewModel: MusicViewModel) {
    val groups by viewModel.duplicateGroups.collectAsState()
    val progress by viewModel.duplicateProgress.collectAsState()
    val running by viewModel.duplicatesRunning.collectAsState()
    val hidden by viewModel.hiddenDuplicates.collectAsState()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Duplicados exactos", color = Color.White,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Button(onClick = { viewModel.analyzeDuplicates(library.songs) }, enabled = !running) {
                Text(if (running) "Analizando…" else "Analizar")
            }
        }
        Text("Solo se consideran duplicados exactos los archivos con el mismo SHA-256. " +
            "Ningún archivo se borra ni se modifica.",
            color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.hideExtraDuplicateCopies() },
                enabled = !running && groups.isNotEmpty()
            ) { Text("Ocultar copias extra") }
            if (hidden.isNotEmpty()) {
                TextButton(onClick = { viewModel.showAllHiddenDuplicateCopies() }) {
                    Text("Mostrar todas")
                }
            }
        }
        if (hidden.isNotEmpty()) {
            Text("${hidden.size} archivos ocultos en AmberMusic",
                color = LocalAmberAccent.current, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp))
        }
        Text(progress, color = LocalAmberAccent.current,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
        LazyColumn {
            if (groups.isEmpty() && !running) item {
                Text("Pulsa Analizar para buscar copias idénticas. El análisis no elimina archivos.",
                    color = Color.LightGray, modifier = Modifier.padding(20.dp))
            }
            groups.forEachIndexed { index, group ->
                item(key = "group_$index") {
                    SectionName("Grupo ${index + 1} · ${group.size} copias")
                }
                items(group, key = { "${index}_${it.id}" }) { song ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        SongArtwork(song, size = 40.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(song.title, color = Color.White, maxLines = 1)
                            Text(song.folderPath + song.displayName,
                                color = Color.Gray, fontSize = 11.sp, maxLines = 2)
                            if (song.contentUri in hidden) {
                                Text("Oculta", color = LocalAmberAccent.current, fontSize = 11.sp)
                            }
                        }
                        TextButton(onClick = { viewModel.keepDuplicateCopy(group, song.id) }) {
                            Text("Conservar", fontSize = 12.sp)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

@Composable
private fun SectionName(text: String) {
    Text(text, color = LocalAmberAccent.current, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, modifier = Modifier.padding(vertical = 16.dp))
}

@Composable
private fun CompactTrack(song: SongEntity, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        SongArtwork(song = song, size = 44.dp)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(song.title, color = Color.White, maxLines = 1)
            Text(song.artistDisplay, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
        }
        Icon(Icons.Filled.PlayArrow, "Reproducir", tint = Color.White)
    }
}

@Composable
private fun SongListBlock(songs: List<SongEntity>, onClick: (Long) -> Unit, onRemove: (Long) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        items(songs, key = { it.id }) { song ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { CompactTrack(song) { onClick(song.id) } }
                IconButton(onClick = { onRemove(song.id) }) { Icon(Icons.Filled.Delete, "Quitar", tint = Color.LightGray) }
            }
        }
        item { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
private fun SettingsEntry(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF111823)).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(23.dp))
        Column(Modifier.weight(1f).padding(start = 15.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Medium)
            Text(description, color = Color.LightGray, fontSize = 12.sp)
        }
        Text("›", color = accent, fontSize = 26.sp)
    }
}
