package dev.liox.ambermusic.ui

import android.net.Uri
import android.content.Intent
import androidx.compose.runtime.LaunchedEffect
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
    startOnSettings: Boolean = false,
    startOnProfiles: Boolean = false,
    onOpenFavorites: () -> Unit = {}
) {
    val accent = LocalAmberAccent.current
    var tab by rememberSaveable(startOnSettings, startOnProfiles) {
        mutableIntStateOf(if (startOnProfiles) 14 else if (startOnSettings) 4 else 0)
    }
    BackHandler(enabled = tab in 5..15) { tab = 4 }
    var playlistId by rememberSaveable { mutableStateOf<Long?>(null) }
    var createDialog by remember { mutableStateOf(false) }
    var showFolders by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Long?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val theme by viewModel.theme.collectAsState()
    val visualStyle by viewModel.visualStyle.collectAsState()
    val density by viewModel.density.collectAsState()
    val reduced by viewModel.reduceMotion.collectAsState()
    val fitCover by viewModel.fitCover.collectAsState()
    val route by viewModel.currentRoute.collectAsState()
    val autoProfiles by viewModel.autoOutputProfiles.collectAsState()
    val routePresets by viewModel.routePresets.collectAsState()
    val offload by viewModel.offload.collectAsState()
    val gain by viewModel.replayGain.collectAsState()
    val soft by viewModel.smoothTransition.collectAsState()
    val immersive by viewModel.immersive.collectAsState()
    val context = LocalContext.current
    val donationPrefs = remember(context) { context.getSharedPreferences("wee_support", android.content.Context.MODE_PRIVATE) }
    var showSupportPrompt by remember { mutableStateOf(false) }
    LaunchedEffect(startOnSettings) {
        if (false) {
            showSupportPrompt = true
        }
    }
    fun openDonationLink() {
        val url = context.getString(dev.liox.ambermusic.R.string.donation_url)
        // No donation destination is opened until the developer configures a personal link.
        if (url == "https://www.paypal.com/donate/") {
            info = "Falta configurar el enlace personal de PayPal antes de publicar."
        } else {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                .onFailure { info = "No se pudo abrir el enlace de PayPal" }
        }
    }
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

    val importM3uPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: error("No se pudo leer la lista")
                }
            }.onSuccess { content -> viewModel.importM3uText(content) { info = it } }
                .onFailure { info = it.localizedMessage ?: "Error leyendo M3U8" }
        }
    }
    var exportM3uId by remember { mutableStateOf<Long?>(null) }
    val exportM3uPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        val id = exportM3uId
        if (uri != null && id != null) scope.launch {
            runCatching {
                val contents = viewModel.exportPlaylistM3u(id)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
                        it.write(contents)
                    } ?: error("No se pudo guardar la lista")
                }
            }.onSuccess { info = "Playlist exportada" }
                .onFailure { info = it.localizedMessage ?: "Error exportando M3U8" }
        }
    }

    Column(Modifier.fillMaxSize().background(LocalAmberPalette.current.background)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (tab in 5..15) tab = 4 else onBack() }) { Icon(Icons.Filled.ArrowBack, "Volver", tint = LocalAmberPalette.current.foreground) }
            Text(when (tab) {
            16 -> "Descargas y HD" 4 -> "Ajustes"; 5 -> "Duplicados"; 6 -> "Biblioteca y carpetas"; 7 -> "Apariencia"; 8 -> "Inmersión visual"; 9 -> "Audio"; 10 -> "Apoyo opcional"; 11 -> "Reproducción"; 12 -> "Rendimiento"; 13 -> "Datos y respaldo"; 14 -> "Perfiles locales"; 15 -> "Mis temas"; else -> "AmberMusic" }, color = LocalAmberPalette.current.foreground, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))

        }
        if (tab in 0..3 || tab == 16) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Playlists", "Estadísticas", "Inteligentes", "Duplicados").forEachIndexed { index, name ->
                FilterChip(selected = index == tab, onClick = { tab = index; playlistId = null }, label = { Text(name) })
            }
        }
        when (tab) {
            16 -> PcToolsPanel(library = library, viewModel = viewModel)
            0 -> {
                val currentId = playlistId
                if (currentId == null) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Mis playlists", color = LocalAmberPalette.current.foreground, modifier = Modifier.weight(1f))
                        TextButton(onClick = { importM3uPicker.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "text/plain", "*/*")) }) {
                            Text("Importar M3U8", color = accent)
                        }
                        TextButton(onClick = { createDialog = true }) { Text("+ Nueva", color = accent) }
                    }
                    LazyColumn {
                        if (library.playlists.isEmpty()) item {
                            Text("Aún no tienes playlists. Pulsa + Nueva para crear la primera.",
                                color = LocalAmberPalette.current.muted, modifier = Modifier.padding(20.dp))
                        }
                        items(library.playlists, key = { it.id }) { playlist ->
                            Row(Modifier.fillMaxWidth().clickable { playlistId = playlist.id }
                                .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.MusicNote, null, tint = accent)
                                Text(playlist.name, color = LocalAmberPalette.current.foreground, modifier = Modifier.weight(1f).padding(start = 14.dp))
                                TextButton(onClick = {
                                    exportM3uId = playlist.id
                                    exportM3uPicker.launch("${playlist.name}.m3u8")
                                }) { Text("M3U8", color = accent, fontSize = 11.sp) }
                                IconButton(onClick = { pendingDelete = playlist.id }) {
                                    Icon(Icons.Filled.Delete, "Eliminar playlist", tint = LocalAmberPalette.current.muted)
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
                        color = LocalAmberPalette.current.foreground, modifier = Modifier.padding(horizontal = 16.dp))
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
            4 -> SettingsHomeV21(viewModel = viewModel, onNavigate = { tab = it })
            14 -> ProfilesScreen(library = library, onOpenThemeEditor = { tab = 15 }, onOpenFavorites = onOpenFavorites)
            15 -> ThemeStudioScreen()
            10 -> LazyColumn(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { SectionName("Apoya el desarrollo de AmberMusic") }
                item { Text("AmberMusic es gratuito, sin anuncios y funciona sin conexión. Si te resulta útil, puedes apoyar su desarrollo con una donación voluntaria. No se desbloquea ninguna función.", color = LocalAmberPalette.current.muted, fontSize = 15.sp) }
                item { Button(onClick = { openDonationLink() }) { Text("Abrir PayPal") } }
                item { Text("Nunca te pediremos una donación para escuchar tu música.", color = LocalAmberPalette.current.muted, fontSize = 12.sp) }
            }
            6 -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                item {
                    SectionName("Carpetas de música")
                    Text("Selecciona las carpetas reconocidas por Android. También se incluyen sus subcarpetas y solo canciones de más de un minuto.", color = LocalAmberPalette.current.muted, fontSize = 13.sp)
                    TextButton(onClick = { viewModel.loadAvailableFolders(); showFolders = true }) { Text("Elegir carpetas", color = accent) }
                    TextButton(onClick = { viewModel.scanLocalMusic() }) { Text("Actualizar biblioteca", color = accent) }
                }
            }
            7 -> AppearanceSettingsV21(viewModel)
            8 -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                item {
                    SectionName("Fondo ambiental")
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Blur dinámico de la portada", color = LocalAmberPalette.current.foreground, modifier = Modifier.weight(1f))
                        Switch(checked = immersive, onCheckedChange = viewModel::setImmersive)
                    }
                    Text("Se genera por portada y se reutiliza desde caché. No se recalcula en cada fotograma.", color = LocalAmberPalette.current.muted, fontSize = 13.sp)
                }
            }
            9 -> AudioSettingsV21(viewModel)
            11 -> PlaybackSettingsV21(viewModel)
            12 -> PerformanceSettingsV21(viewModel)
            13 -> DataSettingsV21(
                onExport = { exportPicker.launch("ambermusic_backup.json") },
                onImport = { importPicker.launch(arrayOf("application/json", "text/plain", "*/*")) },
                onSupport = { tab = 10 }
            )
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
    if (showSupportPrompt) {
        AlertDialog(
            onDismissRequest = { showSupportPrompt = false; donationPrefs.edit().putBoolean("support_prompt_seen", true).apply() },
            title = { Text("¿Te gusta AmberMusic?") },
            text = { Text("AmberMusic seguirá siendo gratis y sin anuncios. Si quieres apoyar su desarrollo, puedes donar voluntariamente por PayPal.") },
            confirmButton = { TextButton(onClick = { showSupportPrompt = false; donationPrefs.edit().putBoolean("support_prompt_seen", true).apply(); tab = 10 }) { Text("Ver cómo apoyar") } },
            dismissButton = { TextButton(onClick = { showSupportPrompt = false; donationPrefs.edit().putBoolean("support_prompt_seen", true).apply() }) { Text("Ahora no") } }
        )
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
            Text("Playlists automáticas", color = LocalAmberPalette.current.foreground, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            TextButton(onClick = { showingCreate = true }) { Text("+ Nueva") }
        }
        Text("Se actualizan solas al añadir música. No duplican archivos.",
            color = LocalAmberPalette.current.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        if (selected == null) {
            LazyColumn {
                if (rules.isEmpty()) item {
                    Text("Sin listas inteligentes. Crea una regla para agrupar canciones automáticamente.",
                        color = LocalAmberPalette.current.muted, modifier = Modifier.padding(20.dp))
                }
                items(rules, key = { it.id }) { rule ->
                    val amount = viewModel.smartSongs(rule, library).size
                    Row(Modifier.fillMaxWidth().clickable { selectedId = rule.id }
                        .padding(horizontal = 17.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MusicNote, null, tint = LocalAmberAccent.current)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(rule.name, color = LocalAmberPalette.current.foreground)
                            Text("${rule.type} · $amount canciones", color = LocalAmberPalette.current.muted, fontSize = 12.sp)
                        }
                        IconButton(onClick = { viewModel.removeSmartPlaylist(rule.id) }) {
                            Icon(Icons.Filled.Delete, "Eliminar regla", tint = LocalAmberPalette.current.muted)
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
            SmartRules.POPULAR to "Muy escuchadas", SmartRules.RECENT to "Últimos 30 días",
            SmartRules.COMBINED to "Favoritas sin escuchar 30 días"
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
                viewModel.createSmartPlaylist(name, type,
                    if (type == SmartRules.COMBINED) "favoritas&no_reciente" else value)
                showingCreate = false
            }, enabled = name.isNotBlank() && (type in listOf(SmartRules.FAVOURITES,
                SmartRules.RECENT, SmartRules.COMBINED) || value.isNotBlank())) { Text("Crear") } },
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
            Text("Duplicados exactos", color = LocalAmberPalette.current.foreground,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Button(onClick = { viewModel.analyzeDuplicates(library.songs) }, enabled = !running) {
                Text(if (running) "Analizando…" else "Analizar")
            }
        }
        Text("Solo se consideran duplicados exactos los archivos con el mismo SHA-256. " +
            "Ningún archivo se borra ni se modifica.",
            color = LocalAmberPalette.current.muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
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
                    color = LocalAmberPalette.current.muted, modifier = Modifier.padding(20.dp))
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
                            Text(song.title, color = LocalAmberPalette.current.foreground, maxLines = 1)
                            Text(song.folderPath + song.displayName,
                                color = LocalAmberPalette.current.muted, fontSize = 11.sp, maxLines = 2)
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
            Text(song.title, color = LocalAmberPalette.current.foreground, maxLines = 1)
            Text(song.artistDisplay, color = LocalAmberPalette.current.muted, fontSize = 12.sp, maxLines = 1)
        }
        Icon(Icons.Filled.PlayArrow, "Reproducir", tint = LocalAmberPalette.current.foreground)
    }
}

@Composable
private fun SongListBlock(songs: List<SongEntity>, onClick: (Long) -> Unit, onRemove: (Long) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        items(songs, key = { it.id }) { song ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(Modifier.weight(1f)) { CompactTrack(song) { onClick(song.id) } }
                IconButton(onClick = { onRemove(song.id) }) { Icon(Icons.Filled.Delete, "Quitar", tint = LocalAmberPalette.current.muted) }
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
            Text(title, color = LocalAmberPalette.current.foreground, fontWeight = FontWeight.Medium)
            Text(description, color = LocalAmberPalette.current.muted, fontSize = 12.sp)
        }
        Text("›", color = accent, fontSize = 26.sp)
    }
}
