package dev.liox.ambermusic.ui

import android.app.RecoverableSecurityException
import android.os.Build
import android.provider.MediaStore
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.runtime.key
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.liox.ambermusic.data.local.SongEntity
import dev.liox.ambermusic.viewmodel.LibraryUiState
import dev.liox.ambermusic.viewmodel.MusicViewModel
import dev.liox.ambermusic.viewmodel.PlayerUiState

private val bg = Color(0xFF000000)
private val panel: Color
    @Composable get() = LocalAmberPalette.current.surface
private val secondary: Color
    @Composable get() = LocalAmberPalette.current.muted
private val gold: Color
    @Composable get() = LocalAmberAccent.current
private val minSongDurationMs = 60_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryAndArtistScreens(
    state: LibraryUiState,
    playing: PlayerUiState,
    viewModel: MusicViewModel,
    onOpenPlayer: () -> Unit,
    externalArtistId: Long?,
    onArtistNavigated: () -> Unit,
    hasAudioPermission: Boolean,
    onRequestPermission: () -> Unit,
    onExitApp: () -> Unit,
    onOpenExtras: () -> Unit,
    onOpenSettings: () -> Unit,
    favoritesOpenRequest: Int
) {
    val hiddenUris by viewModel.hiddenDuplicates.collectAsState()
    val pageHolder = rememberSaveableStateHolder()
    var locateRequest by rememberSaveable { mutableIntStateOf(0) }
    var actionSong by remember { mutableStateOf<SongEntity?>(null) }
    val libraryPreferences = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("amber_library_navigation", android.content.Context.MODE_PRIVATE)
    var selectedTab by rememberSaveable { mutableIntStateOf(libraryPreferences.getInt("selected_tab", 0).coerceIn(0, 3)) }
    LaunchedEffect(selectedTab) { libraryPreferences.edit().putInt("selected_tab", selectedTab).apply() }
    var artistId by rememberSaveable { mutableStateOf<Long?>(null) }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var albumKey by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf("Título") }
    var gridMode by rememberSaveable { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var newCategoryDialog by remember { mutableStateOf(false) }
    var showMainMenu by remember { mutableStateOf(false) }
    var favoritesDetail by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(favoritesOpenRequest) {
        if (favoritesOpenRequest > 0) {
            artistId = null; categoryId = null; albumKey = null
            favoritesDetail = true
            selectedTab = 1
        }
    }
    val openSongs = {
        favoritesDetail = false
        artistId = null; categoryId = null; albumKey = null
        onArtistNavigated()
        selectedTab = 0
        query = ""
        searchExpanded = false
    }
    val activeArtist = externalArtistId ?: artistId
    val activeCategory = categoryId
    val activeAlbum = albumKey
    val inDetail = favoritesDetail || activeArtist != null || activeCategory != null || activeAlbum != null
    val heading = when {
        favoritesDetail -> "Favoritos"
        activeArtist != null -> state.artists.firstOrNull { it.id == activeArtist }?.name ?: "Artista"
        activeCategory != null -> state.categories.firstOrNull { it.id == activeCategory }?.name ?: "Categoría"
        activeAlbum != null -> state.albums.firstOrNull { it.albumKey == activeAlbum }?.albumTitle ?: "Álbum"
        else -> "Biblioteca"
    }
    BackHandler(enabled = inDetail || selectedTab != 0) {
        if (inDetail) {
            favoritesDetail = false; artistId = null; categoryId = null; albumKey = null
            onArtistNavigated()
        } else openSongs()
    }
    val searchable = remember(state.songs, query, sort) {
        val matched = state.songs.filter {
            it.durationMs > minSongDurationMs &&
                (query.isBlank() || it.title.contains(query, ignoreCase = true) ||
                    it.artistDisplay.contains(query, ignoreCase = true) ||
                    it.albumTitle.contains(query, ignoreCase = true) ||
                    it.genre.contains(query, ignoreCase = true) ||
                    it.folderPath.contains(query, ignoreCase = true) ||
                    it.format.contains(query, ignoreCase = true))
        }
        when (sort) {
            "Artista" -> matched.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.artistDisplay })
            "Recientes" -> matched.sortedByDescending { it.modifiedAtSeconds }
            "Duración" -> matched.sortedByDescending { it.durationMs }
            else -> matched.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        }
    }

    PullToRefreshBox(
        isRefreshing = state.scanning,
        onRefresh = { if (hasAudioPermission) viewModel.scanLocalMusic() else onRequestPermission() },
        modifier = Modifier.fillMaxSize()
    ) {
    Column(Modifier.fillMaxSize().background(bg).pointerInput(inDetail, selectedTab) {
        var distance = 0f
        detectHorizontalDragGestures(
            onDragStart = { distance = 0f },
            onDragEnd = {
                if (distance > 100.dp.toPx()) {
                    if (inDetail) {
                        favoritesDetail = false; artistId = null; categoryId = null; albumKey = null
                        onArtistNavigated()
                    } else selectedTab = (selectedTab - 1).coerceAtLeast(0)
                } else if (distance < -100.dp.toPx() && !inDetail) {
                    selectedTab = (selectedTab + 1).coerceAtMost(3)
                }
            },
            onHorizontalDrag = { _, drag -> distance += drag }
        )
    }) {
        Row(
            Modifier.fillMaxWidth().height(62.dp).padding(start = 12.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (inDetail) {
                IconButton(onClick = {
                    favoritesDetail = false
                    artistId = null
                    categoryId = null
                    albumKey = null
                    onArtistNavigated()
                }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White) }
            }
            Text(
                heading, color = Color.White, fontWeight = FontWeight.Bold,
                fontSize = if (inDetail) 22.sp else 28.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 6.dp)
                    .clickable { openSongs() }
            )
            if (!inDetail) {
                IconButton(onClick = {
                    if (searchExpanded) query = ""
                    searchExpanded = !searchExpanded
                }) {
                    Icon(
                        if (searchExpanded) Icons.Filled.Clear else Icons.Filled.Search,
                        contentDescription = if (searchExpanded) "Cerrar búsqueda" else "Buscar canciones",
                        tint = if (searchExpanded) gold else secondary
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Ajustes", tint = secondary)
                }
            }
        }
        when {
            favoritesDetail -> {
                val songs = state.favoriteSongs.filter { it.isVisible && it.durationMs > minSongDurationMs }
                SectionTracks(songs, "${songs.size} canciones favoritas",
                    onClick = { viewModel.playSelection(songs, it) })
            }
            activeArtist != null -> {
                val flow = remember(activeArtist) { viewModel.observeArtistSongs(activeArtist) }
                val artist by flow.collectAsState(initial = null)
                val songs = artist?.songs?.filter {
                    it.isVisible && it.durationMs > minSongDurationMs && it.contentUri !in hiddenUris
                }
                    ?.sortedBy { it.title.lowercase() } ?: emptyList()
                SectionTracks(songs, "${songs.size} canciones · Incluye colaboraciones",
                    onClick = { viewModel.playFromArtist(activeArtist, it) })
            }
            activeCategory != null -> {
                val flow = remember(activeCategory) { viewModel.observeCategorySongs(activeCategory) }
                val songs by flow.collectAsState(initial = emptyList())
                val visibleSongs = songs.filter {
                    it.durationMs > minSongDurationMs && it.contentUri !in hiddenUris
                }
                SectionTracks(visibleSongs, "${visibleSongs.size} canciones",
                    onClick = { viewModel.playFromCategory(activeCategory, it) },
                    onRemove = { viewModel.removeFromCategory(it, activeCategory) })
            }
            activeAlbum != null -> {
                val trackFlow = remember(activeAlbum) { viewModel.observeAlbumSongs(activeAlbum) }
                val tracks by trackFlow.collectAsState(initial = emptyList())
                val songs = tracks.filter { it.isVisible && it.durationMs > minSongDurationMs && it.contentUri !in hiddenUris }
                SectionTracks(songs, "${songs.size} canciones · Orden original del álbum",
                    onClick = { viewModel.playFromAlbum(activeAlbum, it) })
            }
            else -> {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab, edgePadding = 12.dp,
                    containerColor = bg, contentColor = gold,
                    divider = {}
                ) {
                    listOf("Canciones", "Categorías", "Artistas", "Álbumes").forEachIndexed { index, title ->
                        Tab(selected = selectedTab == index, onClick = { selectedTab = index },
                            selectedContentColor = Color.White, unselectedContentColor = secondary,
                            text = { Text(title, maxLines = 1) })
                    }
                }
                AnimatedVisibility(
                    visible = searchExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text("Buscar canciones, artistas o álbumes", fontSize = 13.sp, color = secondary) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = secondary) },
                        trailingIcon = if (query.isNotEmpty()) {{
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Limpiar búsqueda", tint = secondary)
                            }
                        }} else null,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
                if (!hasAudioPermission) {
                    Button(onClick = onRequestPermission, modifier = Modifier.padding(16.dp)) {
                        Text("Permitir acceso a la música")
                    }
                }
                if (state.scanning) Text("Actualizando biblioteca…", color = gold,
                    modifier = Modifier.padding(horizontal = 19.dp, vertical = 6.dp))
                state.message?.let { message ->
                    if (message.contains("No se pudo", ignoreCase = true) ||
                        message.contains("Error leyendo", ignoreCase = true)) {
                        Text(message, color = Color(0xFFF5A8A8), fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 5.dp))
                    }
                }
                pageHolder.SaveableStateProvider("tab_$selectedTab") {
                when (selectedTab) {
                    0 -> {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 20.dp, end = 13.dp, top = 10.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Canciones", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                                Text("${searchable.size} canciones · Más de 1 min", color = secondary, fontSize = 12.sp)
                            }
                            Box {
                                IconButton(onClick = { showSort = true }) {
                                    Icon(Icons.Filled.Sort, contentDescription = "Ordenar", tint = Color.White)
                                }
                                DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false },
                                    modifier = Modifier.background(panel)) {
                                    listOf("Título", "Artista", "Recientes", "Duración").forEach { item ->
                                        DropdownMenuItem(text = { Text(item, color = Color.White) }, onClick = {
                                            sort = item; showSort = false
                                        })
                                    }
                                }
                            }
                            IconButton(onClick = { gridMode = !gridMode }) {
                                Icon(if (gridMode) Icons.Filled.ViewList else Icons.Filled.GridView,
                                    contentDescription = if (gridMode) "Vista lista" else "Vista cuadrícula", tint = Color.White)
                            }
                        }
                        if (gridMode) {
                            TracksGrid(searchable, onClick = { viewModel.playSelection(searchable, it) }, currentSongId = playing.song?.id)
                        } else {
                            SectionTracks(searchable, null,
                                scrollPreferenceKey = "songs_main",
                                onClick = { viewModel.playSelection(searchable, it) },
                                currentSongId = playing.song?.id, locateRequest = locateRequest,
                                onFavorite = viewModel::toggleFavorite, onPlayNext = viewModel::playNext,
                                onActions = { actionSong = it })
                        }
                    }
                    1 -> {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Mis categorías", color = Color.White, fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { newCategoryDialog = true }) {
                                Icon(Icons.Filled.Add, contentDescription = "Nueva categoría", tint = gold)
                            }
                        }
                        LazyColumn {
                            if (query.isBlank() || "Favoritos".contains(query, ignoreCase = true)) {
                                item(key = "virtual_favorites") {
                                    Row(Modifier.fillMaxWidth().clickable { favoritesDetail = true }
                                        .padding(horizontal = 22.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF10273C)), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Filled.Favorite, contentDescription = null, tint = gold)
                                        }
                                        Spacer(Modifier.width(14.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text("Favoritos", color = Color.White, fontWeight = FontWeight.SemiBold)
                                            Text("${state.favoriteSongs.size} canciones", color = secondary, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                            items(state.categories.filter { it.name.contains(query, ignoreCase = true) }, key = { it.id }) { category ->
                                Row(Modifier.fillMaxWidth().clickable { categoryId = category.id }
                                    .padding(horizontal = 22.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(panel),
                                        contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Folder, contentDescription = null, tint = gold)
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Text(category.name, color = Color.White, modifier = Modifier.weight(1f),
                                        fontWeight = FontWeight.Medium)
                                    IconButton(onClick = { viewModel.deleteCategory(category.id) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar categoría", tint = secondary)
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(95.dp)) }
                        }
                    }
                    2 -> LazyColumn {
                        item { Text("Artistas", color = Color.White, fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) }
                        items(state.artists.filter { it.name.contains(query, ignoreCase = true) }, key = { it.id }) { artist ->
                            Row(Modifier.fillMaxWidth().clickable { artistId = artist.id }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(50.dp).clip(CircleShape).background(panel),
                                    contentAlignment = Alignment.Center) {
                                    Text(artist.name.trim().take(1).uppercase(), color = gold,
                                        fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.width(15.dp))
                                Text(artist.name, color = Color.White, modifier = Modifier.weight(1f))
                            }
                        }
                        item { Spacer(Modifier.height(100.dp)) }
                    }
                    else -> {
                        val albums = state.albums.filter { album ->
                            album.albumTitle.contains(query, ignoreCase = true) ||
                                state.songs.any { it.albumKey == album.albumKey && it.artistDisplay.contains(query, ignoreCase = true) }
                        }
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            val cell = (maxWidth - 48.dp) / 2f
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(18.dp)
                            ) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text("Álbumes", color = Color.White, fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                                }
                                items(albums, key = { it.albumKey }) { album ->
                                    val coverSong = remember(album.albumKey, state.songs) {
                                        state.songs.firstOrNull { it.albumKey == album.albumKey }
                                    }
                                    Column(Modifier.clickable { albumKey = album.albumKey }) {
                                        SongArtwork(song = coverSong, size = cell,
                                            modifier = Modifier.clip(RoundedCornerShape(11.dp)))
                                        Spacer(Modifier.height(8.dp))
                                        Text(album.albumTitle, color = Color.White, fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold, maxLines = 1,
                                            overflow = TextOverflow.Ellipsis)
                                        Text("${album.songCount} canciones", color = secondary, fontSize = 11.sp)
                                    }
                                }
                                item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(100.dp)) }
                            }
                        }
                    }
                }
                }
            }
        }
    }
    }

    val deleteContext = androidx.compose.ui.platform.LocalContext.current
    var deleteTarget by remember { mutableStateOf<SongEntity?>(null) }
    var pendingDeleteUri by remember { mutableStateOf<String?>(null) }
    var deleteInfo by remember { mutableStateOf<String?>(null) }
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            deleteInfo = "Archivo eliminado. Actualiza la biblioteca para reflejar el cambio."
            viewModel.scanLocalMusic()
        } else deleteInfo = "No se eliminó ningún archivo."
        pendingDeleteUri = null
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Eliminar archivo de música") },
            text = { Text("¿Eliminar definitivamente '${target.title}' del teléfono? Esta acción afecta al archivo original y requiere autorización de Android.") },
            confirmButton = { TextButton(onClick = {
                deleteTarget = null
                runCatching {
                    val uri = Uri.parse(target.contentUri)
                    val sender = MediaStore.createDeleteRequest(deleteContext.contentResolver, listOf(uri)).intentSender
                    pendingDeleteUri = target.contentUri
                    deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }.onFailure { deleteInfo = it.localizedMessage ?: "No se pudo solicitar la eliminación" }
            }) { Text("Solicitar eliminación") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } }
        )
    }
    deleteInfo?.let { message ->
        AlertDialog(onDismissRequest = { deleteInfo = null }, title = { Text("Archivos") },
            text = { Text(message) }, confirmButton = { TextButton(onClick = { deleteInfo = null }) { Text("Aceptar") } })
    }
    actionSong?.let { target ->
        AlertDialog(
            onDismissRequest = { actionSong = null },
            title = { Text(target.title, maxLines = 1) },
            text = {
                Column {
                    TextButton(onClick = { viewModel.playNext(target); actionSong = null }) { Text("Reproducir siguiente") }
                    TextButton(onClick = { viewModel.toggleFavorite(target.id); actionSong = null }) { Text("Cambiar favorito") }
                    TextButton(onClick = { deleteTarget = target; actionSong = null }) { Text("Eliminar del teléfono", color = Color(0xFFEBA9B5)) }
                    Text("Desliza a la derecha para la cola; a la izquierda para Favoritos.", color = secondary)
                }
            },
            confirmButton = { TextButton(onClick = { actionSong = null }) { Text("Cerrar") } }
        )
    }
    if (newCategoryDialog) {
        CategoryCreateDialog(onDismiss = { newCategoryDialog = false },
            onConfirm = { viewModel.createCategory(it); newCategoryDialog = false })
    }
}

@Composable
private fun CategoryCreateDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var title by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva categoría") },
        text = {
            OutlinedTextField(value = title, onValueChange = { title = it.take(80) },
                label = { Text("Gym, Chill, OST…") }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title.trim()) }, enabled = title.isNotBlank()) {
                Text("Crear")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun SectionTracks(
    tracks: List<SongEntity>,
    subtitle: String?,
    onClick: (Long) -> Unit,
    onRemove: ((Long) -> Unit)? = null,
    currentSongId: Long? = null,
    locateRequest: Int = 0,
    onFavorite: ((Long) -> Unit)? = null,
    onPlayNext: ((SongEntity) -> Unit)? = null,
    onActions: ((SongEntity) -> Unit)? = null,
    scrollPreferenceKey: String? = null
) {
    val preferenceStore = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("amber_library_navigation", android.content.Context.MODE_PRIVATE)
    val initialIndex = if (scrollPreferenceKey == null) 0 else preferenceStore.getInt("${scrollPreferenceKey}_index", 0).coerceAtLeast(0)
    val initialOffset = if (scrollPreferenceKey == null) 0 else preferenceStore.getInt("${scrollPreferenceKey}_offset", 0).coerceAtLeast(0)
    val listState = rememberLazyListState(initialIndex, initialOffset)
    LaunchedEffect(listState, scrollPreferenceKey) {
        if (scrollPreferenceKey != null) {
            androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .collect { (index, offset) ->
                    preferenceStore.edit().putInt("${scrollPreferenceKey}_index", index)
                        .putInt("${scrollPreferenceKey}_offset", offset).apply()
                }
        }
    }
    LaunchedEffect(locateRequest) {
        if (locateRequest > 0) {
            val idx = tracks.indexOfFirst { it.id == currentSongId }
            if (idx >= 0) listState.animateScrollToItem(idx + if (subtitle == null) 0 else 1)
        }
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        if (subtitle != null) {
            item {
                Text(subtitle, color = secondary, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 12.dp))
            }
        }
        items(tracks, key = { it.id }) { song ->
            Row(Modifier.fillMaxWidth()
                .pointerInput(song.id, onFavorite, onPlayNext) {
                    var swipe = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { swipe = 0f },
                        onHorizontalDrag = { event, dx -> event.consume(); swipe += dx },
                        onDragEnd = {
                            if (swipe > 95.dp.toPx()) onPlayNext?.invoke(song)
                            if (swipe < -95.dp.toPx()) onFavorite?.invoke(song.id)
                        }
                    )
                }
                .combinedClickable(onClick = { onClick(song.id) }, onLongClick = { onActions?.invoke(song) })
                .padding(horizontal = 18.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically) {
                SongArtwork(song = song, size = 56.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, color = if (song.id == currentSongId) gold else Color.White, fontWeight = FontWeight.Medium,
                        fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(5.dp))
                    Text(song.artistDisplay, color = secondary, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (onRemove != null) {
                    IconButton(onClick = { onRemove(song.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Quitar de categoría", tint = secondary)
                    }
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir", tint = if (song.id == currentSongId) gold else secondary)
                }
            }
        }
        item { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
private fun TracksGrid(tracks: List<SongEntity>, onClick: (Long) -> Unit,
    currentSongId: Long? = null) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cell = (maxWidth - 48.dp) / 2f
        LazyVerticalGrid(
            columns = GridCells.Fixed(2), state = rememberLazyGridState(),
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            items(tracks, key = { it.id }) { song ->
                Column(Modifier.clickable { onClick(song.id) }) {
                    SongArtwork(song = song, size = cell)
                    Spacer(Modifier.height(8.dp))
                    Text(song.title, color = if (song.id == currentSongId) gold else Color.White, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artistDisplay, color = secondary, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(100.dp)) }
        }
    }
}
