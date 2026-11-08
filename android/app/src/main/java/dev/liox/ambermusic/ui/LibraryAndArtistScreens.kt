package dev.liox.ambermusic.ui

import android.app.RecoverableSecurityException
import android.os.Build
import android.provider.MediaStore
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import org.koin.compose.koinInject
import dev.liox.ambermusic.artwork.ArtworkRepository
import dev.liox.ambermusic.visual.ImmersiveBackdrop
import dev.liox.ambermusic.settings.AmberSettings
import androidx.compose.runtime.produceState
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
import dev.liox.ambermusic.domain.usecase.SearchMatcher
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.foundation.border
import dev.liox.ambermusic.profiles.ProfileStore
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
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import org.koin.androidx.compose.koinViewModel
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

private val bg: Color
    @Composable get() = LocalAmberPalette.current.background
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
    onOpenProfiles: () -> Unit,
    favoritesOpenRequest: Int,
    searchRequest: Int = 0
) {
    val hiddenUris by viewModel.hiddenDuplicates.collectAsState()
    val pageHolder = rememberSaveableStateHolder()
    var locateRequest by rememberSaveable { mutableIntStateOf(0) }
    var actionSong by remember { mutableStateOf<SongEntity?>(null) }
    val libraryPreferences = androidx.compose.ui.platform.LocalContext.current.getSharedPreferences("amber_library_navigation", android.content.Context.MODE_PRIVATE)
    var selectedTab by rememberSaveable { mutableIntStateOf(libraryPreferences.getInt("selected_tab", 0).coerceIn(0, 4)) }
    LaunchedEffect(selectedTab) { libraryPreferences.edit().putInt("selected_tab", selectedTab).apply() }
    var artistId by rememberSaveable { mutableStateOf<Long?>(null) }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var albumKey by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(searchRequest) { if (searchRequest > 0) searchExpanded = true }
    // Persist the user's arrival order independently of the activity and process lifecycle.
    var sortResetCounter by remember { mutableIntStateOf(0) }
    var sort by rememberSaveable {
        mutableStateOf(libraryPreferences.getString("arrival_order", "newest")
            ?.takeIf { it == "newest" || it == "oldest" } ?: "newest")
    }
    LaunchedEffect(sort) { libraryPreferences.edit().putString("arrival_order", sort).apply() }
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
        else -> "Tu biblioteca"
    }
    BackHandler(enabled = inDetail || selectedTab != 0) {
        if (inDetail) {
            favoritesDetail = false; artistId = null; categoryId = null; albumKey = null
            onArtistNavigated()
        } else openSongs()
    }
    // Build the accent-folded search index only when the library actually changes.
    // Typing a character no longer normalizes hundreds of fields on every recomposition.
    val songSearchIndex = remember(state.songs) {
        state.songs.associate { song -> song.id to SearchMatcher.makeIndex(
            song.title, song.artistDisplay, song.albumTitle,
            song.genre, song.folderPath, song.format
        ) }
    }
    val searchable = remember(state.songs, songSearchIndex, query, sort) {
        val normalizedQuery = SearchMatcher.normalize(query)
        val matched = state.songs.filter {
            it.durationMs > minSongDurationMs &&
                SearchMatcher.matchesIndex(normalizedQuery, songSearchIndex[it.id].orEmpty())
        }
        val byArrival = matched.sortedWith(compareBy<SongEntity> {
            it.dateAddedSeconds.takeIf { seconds -> seconds > 0L } ?: it.modifiedAtSeconds
        }.thenBy { it.id })
        if (sort == "oldest") byArrival else byArrival.asReversed()
    }

    val settingsForHome: AmberSettings = koinInject()
    val profiles: ProfileStore = koinInject()
    val selectedProfile by profiles.active.collectAsState()
    val libraryImmersive by settingsForHome.immersive.collectAsState()
    val visualStyle by settingsForHome.visualStyle.collectAsState()
    val customBackground by settingsForHome.customBackground.collectAsState()
    val wallpaperOpacity by settingsForHome.wallpaperOpacity.collectAsState()
    val allowBackdrop = false // Keep the library truly AMOLED-black; only player has atmospheric gradients.
    val coverStore: ArtworkRepository = koinInject()
    val ambientStore: ImmersiveBackdrop = koinInject()
    val coverRevision by coverStore.version.collectAsState()
    val ambientSong = playing.song
    val homeAmbient by produceState<dev.liox.ambermusic.visual.AmbientArtwork?>(
        null, ambientSong?.id, coverRevision, allowBackdrop
    ) {
        value = if (allowBackdrop && ambientSong != null)
            ambientStore.forCover(coverStore.fileFor(ambientSong)) else null
    }

    PullToRefreshBox(
        isRefreshing = state.scanning,
        onRefresh = { if (hasAudioPermission) viewModel.scanLocalMusic() else onRequestPermission() },
        modifier = Modifier.fillMaxSize()
    ) {
    Box(Modifier.fillMaxSize().background(bg)) {
        // Reuse the precomputed ambient bitmap. No runtime blur on scrolling frames.
        Crossfade(homeAmbient?.file, animationSpec = tween(650), label = "Biblioteca ambiental") { file ->
            if (file != null && allowBackdrop) {
                AsyncImage(model = file, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().alpha(0.40f))
            }
        }
        if (customBackground.isNotBlank()) {
            AsyncImage(model = customBackground, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().alpha(wallpaperOpacity))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            0f to bg.copy(alpha = if (visualStyle == "light") .73f else .55f),
            0.35f to bg.copy(alpha = if (visualStyle == "light") .83f else .78f),
            1f to bg.copy(alpha = .96f)
        )))
    // Tabs are selected by tapping. Do not steal Android edge-back gestures.
    Column(Modifier.fillMaxSize()) {
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
                }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Volver", tint = LocalAmberPalette.current.foreground) }
            }
            Column(Modifier.weight(1f).padding(start = 5.dp)) {
                Text(heading, color = LocalAmberPalette.current.foreground,
                    fontWeight = FontWeight.Bold, fontSize = if (inDetail) 22.sp else 25.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!inDetail) Text("${state.songs.size} canciones · ${selectedProfile.name}",
                    color = secondary, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!inDetail) {
                IconButton(onClick = onOpenProfiles) {
                    if (!selectedProfile.avatar.isNullOrBlank()) {
                        AsyncImage(model = selectedProfile.avatar, contentDescription = "Perfiles locales",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(33.dp).clip(CircleShape)
                                .border(1.dp, LocalAmberAccent.current, CircleShape))
                    } else Icon(Icons.Filled.AccountCircle, contentDescription = "Perfiles locales",
                        tint = LocalAmberAccent.current, modifier = Modifier.size(31.dp))
                }
            }
        }
        if (!inDetail) {
            androidx.compose.material3.TextField(
                value = query,
                onValueChange = { query = it; searchExpanded = true },
                singleLine = true,
                placeholder = { Text("Canción, artista o álbum", color = secondary, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = secondary, modifier = Modifier.size(20.dp)) },
                trailingIcon = {
                    if (query.isNotBlank()) IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Clear, "Limpiar", tint = secondary, modifier = Modifier.size(18.dp))
                    } else Icon(Icons.Filled.Tune, contentDescription = "Buscar", tint = secondary, modifier = Modifier.size(18.dp))
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp).height(50.dp)
                    .clip(RoundedCornerShape(28.dp)),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = LocalAmberPalette.current.surface,
                    unfocusedContainerColor = LocalAmberPalette.current.surface,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                )
            )
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
                    onClick = { viewModel.playFromAlbum(activeAlbum, it) },
                    header = {
                        AlbumDetailHero(
                            title = heading,
                            songs = songs,
                            onPlay = { songs.firstOrNull()?.let { viewModel.playFromAlbum(activeAlbum, it.id) } },
                            onShuffle = {
                                if (!playing.playback.shuffleModeEnabled) viewModel.toggleShuffle()
                                songs.firstOrNull()?.let { viewModel.playFromAlbum(activeAlbum, it.id) }
                            }
                        )
                    })
            }
            else -> {
                if (query.isBlank()) {
                    LibrarySpotlightSection(
                        current = playing.song,
                        recent = state.recentlyPlayed.take(8),
                        favoritesCount = state.favoriteSongs.size,
                        playlistsCount = state.playlists.size,
                        onPlayRecent = { id ->
                            viewModel.playSelection(state.recentlyPlayed, id)
                            onOpenPlayer()
                        },
                        onOpenPlayer = onOpenPlayer
                    )
                }
                ScrollableTabRow(
                    selectedTabIndex = selectedTab, edgePadding = 16.dp,
                    containerColor = Color.Transparent, contentColor = gold,
                    divider = {}, indicator = {}
                ) {
                    listOf("Todo", "Canciones", "Álbumes", "Artistas", "Categorías").forEachIndexed { index, title ->
                        Tab(selected = selectedTab == index, onClick = { selectedTab = index },
                            selectedContentColor = LocalAmberPalette.current.foreground, unselectedContentColor = secondary,
                            text = {
                                Box(Modifier.clip(RoundedCornerShape(24.dp))
                                    .background(if (selectedTab == index) LocalAmberAccent.current else LocalAmberPalette.current.surface)
                                    .padding(horizontal = 12.dp, vertical = 7.dp)) {
                                    Text(title, maxLines = 1, color = if (selectedTab == index)
                                        if (visualStyle == "light") Color.White else Color.Black
                                        else LocalAmberPalette.current.foreground)
                                }
                            })
                    }
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
                    0, 1 -> {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 20.dp, end = 13.dp, top = 10.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                            Text("Canciones", color = LocalAmberPalette.current.foreground, fontSize = 16.sp,
                                fontWeight = FontWeight.Bold)
                            Text("${searchable.size} canciones", color = secondary, fontSize = 11.sp)
                        }
                            Box {
                                IconButton(onClick = { showSort = true }) {
                                    Icon(Icons.Filled.Sort, contentDescription = "Ordenar", tint = LocalAmberPalette.current.foreground)
                                }
                                DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false },
                                    modifier = Modifier.background(panel)) {
                                    listOf("newest" to "Más recientes primero", "oldest" to "Más antiguos primero")
                                        .forEach { (id, label) ->
                                            DropdownMenuItem(text = {
                                                Text(if (sort == id) "✓ $label" else label, color = LocalAmberPalette.current.foreground)
                                            }, onClick = {
                                                if (sort != id) { sort = id; sortResetCounter++ }
                                                showSort = false
                                            })
                                        }
                                }
                            }
                            IconButton(onClick = { gridMode = !gridMode }) {
                                Icon(if (gridMode) Icons.Filled.ViewList else Icons.Filled.GridView,
                                    contentDescription = if (gridMode) "Vista lista" else "Vista cuadrícula", tint = LocalAmberPalette.current.foreground)
                            }
                        }
                        if (gridMode) {
                            TracksGrid(searchable, onClick = { viewModel.playSelection(searchable, it) }, currentSongId = playing.song?.id)
                        } else {
                            SectionTracks(searchable, null,
                                scrollPreferenceKey = "songs_main",
                                resetRequest = sortResetCounter,
                                onClick = { viewModel.playSelection(searchable, it) },
                                currentSongId = playing.song?.id, locateRequest = locateRequest,
                                onFavorite = viewModel::toggleFavorite, onPlayNext = viewModel::playNext,
                                onActions = { actionSong = it })
                        }
                    }
                    4 -> {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("Mis categorías", color = LocalAmberPalette.current.foreground, fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { newCategoryDialog = true }) {
                                Icon(Icons.Filled.Add, contentDescription = "Nueva categoría", tint = gold)
                            }
                        }
                        LazyColumn {
                            if (query.isBlank() || SearchMatcher.matches(query, "Favoritos")) {
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
                                            Text("Favoritos", color = LocalAmberPalette.current.foreground, fontWeight = FontWeight.SemiBold)
                                            Text("${state.favoriteSongs.size} canciones", color = secondary, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                            items(state.categories.filter { SearchMatcher.matches(query, it.name) }, key = { it.id }) { category ->
                                Row(Modifier.fillMaxWidth().clickable { categoryId = category.id }
                                    .padding(horizontal = 22.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(panel),
                                        contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Folder, contentDescription = null, tint = gold)
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Text(category.name, color = LocalAmberPalette.current.foreground, modifier = Modifier.weight(1f),
                                        fontWeight = FontWeight.Medium)
                                    IconButton(onClick = { viewModel.deleteCategory(category.id) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Eliminar categoría", tint = secondary)
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(95.dp)) }
                        }
                    }
                    3 -> LazyColumn {
                        item { Text("Artistas", color = LocalAmberPalette.current.foreground, fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) }
                        items(state.artists.filter { SearchMatcher.matches(query, it.name) }, key = { it.id }) { artist ->
                            Row(Modifier.fillMaxWidth().clickable { artistId = artist.id }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(50.dp).clip(CircleShape).background(panel),
                                    contentAlignment = Alignment.Center) {
                                    Text(artist.name.trim().take(1).uppercase(), color = gold,
                                        fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(Modifier.width(15.dp))
                                Text(artist.name, color = LocalAmberPalette.current.foreground, modifier = Modifier.weight(1f))
                            }
                        }
                        item { Spacer(Modifier.height(172.dp)) }
                    }
                    2 -> {
                        val albums = state.albums.filter { album ->
                            SearchMatcher.matches(query, album.albumTitle) ||
                                state.songs.any { it.albumKey == album.albumKey && SearchMatcher.matches(query, it.artistDisplay) }
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
                                    Text("Álbumes", color = LocalAmberPalette.current.foreground, fontSize = 17.sp,
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
                                        Text(album.albumTitle, color = LocalAmberPalette.current.foreground, fontSize = 13.sp,
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
private fun LibrarySpotlightSection(
    current: SongEntity?,
    recent: List<SongEntity>,
    favoritesCount: Int,
    playlistsCount: Int,
    onPlayRecent: (Long) -> Unit,
    onOpenPlayer: () -> Unit
) {
    if (recent.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 19.dp, end = 19.dp, top = 8.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Seguir escuchando", color = LocalAmberPalette.current.foreground,
                fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("Ver todo", color = LocalAmberAccent.current, fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onOpenPlayer))
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 17.dp)
        ) {
            items(recent.take(8), key = { it.id }) { song ->
                Column(Modifier.width(112.dp).clickable { onPlayRecent(song.id) }) {
                    SongArtwork(song = song, size = 112.dp,
                        modifier = Modifier.clip(RoundedCornerShape(17.dp)))
                    Spacer(Modifier.height(6.dp))
                    Text(song.title, color = LocalAmberPalette.current.foreground,
                        fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artistDisplay, color = LocalAmberPalette.current.muted,
                        fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun AlbumDetailHero(title: String, songs: List<SongEntity>, onPlay: () -> Unit, onShuffle: () -> Unit) {
    val accent = LocalAmberAccent.current
    Column(
        Modifier.fillMaxWidth().background(
            Brush.verticalGradient(listOf(accent.copy(alpha = 0.23f), Color.Transparent))
        ).padding(horizontal = 18.dp, vertical = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        SongArtwork(song = songs.firstOrNull(), size = 164.dp,
            modifier = Modifier.clip(RoundedCornerShape(26.dp)))
        Text(title, color = LocalAmberPalette.current.foreground, fontSize = 23.sp,
            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            listOfNotNull(songs.firstOrNull()?.artistDisplay,
                "${songs.size} canciones", "${songs.sumOf { it.durationMs } / 60000} min").joinToString(" · "),
            color = LocalAmberPalette.current.muted, fontSize = 12.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onPlay, enabled = songs.isNotEmpty()) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Reproducir")
            }
            androidx.compose.material3.OutlinedButton(onClick = onShuffle, enabled = songs.isNotEmpty()) {
                Icon(Icons.Filled.Shuffle, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Aleatorio")
            }
        }
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
    scrollPreferenceKey: String? = null,
    resetRequest: Int = 0,
    header: (@Composable () -> Unit)? = null
) {
    val densitySettings: AmberSettings = koinInject()
    val rowDensity by densitySettings.density.collectAsState()
    val rowSize = if (rowDensity == "compact") 44.dp else 52.dp
    val rowPadding = if (rowDensity == "compact") 5.dp else 8.dp
    val bulkViewModel: MusicViewModel = koinViewModel()
    val fullLibrary by bulkViewModel.libraryUiState.collectAsState()
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var bulkPlaylistDialog by remember { mutableStateOf(false) }
    var chosenPlaylists by remember { mutableStateOf<Set<Long>>(emptySet()) }
    LaunchedEffect(tracks) {
        selectedIds = selectedIds.intersect(tracks.asSequence().map { it.id }.toSet())
    }
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
    LaunchedEffect(resetRequest) {
        if (resetRequest > 0) listState.scrollToItem(0)
    }
    LaunchedEffect(locateRequest) {
        if (locateRequest > 0) {
            val idx = tracks.indexOfFirst { it.id == currentSongId }
            if (idx >= 0) listState.animateScrollToItem(idx + if (subtitle == null) 0 else 1)
        }
    }
    if (bulkPlaylistDialog) {
        AlertDialog(onDismissRequest = { bulkPlaylistDialog = false },
            title = { Text("Añadir a playlists") },
            text = { Column {
                fullLibrary.playlists.forEach { playlist ->
                    Row(Modifier.fillMaxWidth().clickable {
                        chosenPlaylists = if (playlist.id in chosenPlaylists)
                            chosenPlaylists - playlist.id else chosenPlaylists + playlist.id
                    }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = playlist.id in chosenPlaylists, onCheckedChange = {
                            chosenPlaylists = if (it) chosenPlaylists + playlist.id
                                else chosenPlaylists - playlist.id
                        })
                        Text(playlist.name, maxLines = 1)
                    }
                }
            } },
            confirmButton = { TextButton(onClick = {
                bulkViewModel.addManyToPlaylists(selectedIds, chosenPlaylists)
                selectedIds = emptySet(); chosenPlaylists = emptySet(); bulkPlaylistDialog = false
            }) { Text("Añadir") } },
            dismissButton = { TextButton(onClick = { bulkPlaylistDialog = false }) { Text("Cancelar") } })
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        if (header != null) item(key = "detail_hero") { header() }
        if (selectedIds.isNotEmpty()) {
            item(key = "bulk_actions") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("${selectedIds.size} seleccionadas", modifier = Modifier.weight(1f),
                        color = gold, fontSize = 12.sp)
                    TextButton(onClick = {
                        bulkViewModel.favoriteMany(selectedIds); selectedIds = emptySet()
                    }) { Text("Favoritas") }
                    TextButton(onClick = { bulkPlaylistDialog = true }) { Text("Playlists") }
                    TextButton(onClick = { selectedIds = emptySet() }) { Text("Cerrar") }
                }
            }
        }
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
                .background(if (song.id in selectedIds) gold.copy(alpha = 0.13f) else Color.Transparent)
                .combinedClickable(
                    onClick = {
                        if (selectedIds.isNotEmpty()) selectedIds = if (song.id in selectedIds)
                            selectedIds - song.id else selectedIds + song.id
                        else onClick(song.id)
                    },
                    onLongClick = { selectedIds = if (song.id in selectedIds)
                        selectedIds - song.id else selectedIds + song.id }
                )
                .padding(horizontal = 18.dp, vertical = rowPadding),
                verticalAlignment = Alignment.CenterVertically) {
                SongArtwork(song = song, size = rowSize)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, color = if (song.id == currentSongId) gold else LocalAmberPalette.current.foreground, fontWeight = FontWeight.Medium,
                        fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(song.artistDisplay, color = secondary, fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (song.id in selectedIds) {
                    Checkbox(checked = true, onCheckedChange = {
                        selectedIds = selectedIds - song.id
                    })
                } else if (onRemove != null) {
                    IconButton(onClick = { onRemove(song.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Quitar de categoría", tint = secondary)
                    }
                } else if (song.id == currentSongId) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(gold))
                }
            }
        }
        item { Spacer(Modifier.height(172.dp)) }
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
                    Text(song.title, color = if (song.id == currentSongId) gold else LocalAmberPalette.current.foreground, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artistDisplay, color = secondary, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(100.dp)) }
        }
    }
}
