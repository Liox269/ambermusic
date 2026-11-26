package dev.liox.ambermusic.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.liox.ambermusic.profiles.ProfileStore
import dev.liox.ambermusic.viewmodel.LibraryUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/** Profiles never duplicate audio files and the default profile keeps legacy Room favorites. */
@Composable
fun ProfilesScreen(library: LibraryUiState, onOpenThemeEditor: () -> Unit, onOpenFavorites: () -> Unit) {
    val store: ProfileStore = koinInject()
    val profiles by store.profiles.collectAsState()
    val active by store.active.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var createDialog by remember { mutableStateOf(false) }
    var renameDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var editName by remember(active.id) { mutableStateOf(active.name) }
    var info by remember { mutableStateOf<String?>(null) }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            store.setAvatar(active.id, uri.toString())
        }
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val text = store.exportProfile(active.id,
                    if (active.id == "default") library.favoriteSongs.map { it.contentUri }.toSet() else emptySet())
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
                        ?: error("No se puede crear el archivo")
                }
            }.onSuccess { info = "Perfil exportado. La música original no se incluye." }
                .onFailure { info = it.localizedMessage }
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("No se pudo leer el archivo")
                }
                store.importProfile(text)
            }.onSuccess { info = if (it) "Perfil importado" else "Archivo inválido o límite de perfiles alcanzado" }
                .onFailure { info = it.localizedMessage }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 17.dp),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = LocalAmberPalette.current.surfaceRaised),
                shape = RoundedCornerShape(24.dp)) {
                Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(active.avatar, Modifier.size(72.dp).clickable { avatarPicker.launch(arrayOf("image/*")) })
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(active.name, color = LocalAmberPalette.current.foreground,
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Perfil local · Sin cuenta", color = LocalAmberPalette.current.muted,
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { renameDialog = true }, contentPadding = PaddingValues(0.dp)) {
                            Text("Editar nombre e imagen", color = LocalAmberAccent.current)
                        }
                    }
                }
            }
        }
        item { Text("Cambiar de perfil", style = MaterialTheme.typography.titleMedium,
            color = LocalAmberPalette.current.foreground) }
        items(profiles, key = { it.id }) { profile ->
            val selected = profile.id == active.id
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(if (selected) LocalAmberPalette.current.surfaceRaised else LocalAmberPalette.current.surface)
                .clickable { store.select(profile.id) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(profile.avatar, Modifier.size(43.dp))
                Spacer(Modifier.width(12.dp))
                Text(profile.name, color = LocalAmberPalette.current.foreground,
                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = "Activo", tint = LocalAmberAccent.current)
            }
        }
        item {
            OutlinedButton(onClick = { editName = ""; createDialog = true }, enabled = profiles.size < 8,
                modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(7.dp)); Text("Crear perfil")
            }
        }
        item {
            Text("Mi música", style = MaterialTheme.typography.titleMedium,
                color = LocalAmberPalette.current.foreground)
            Spacer(Modifier.height(7.dp))
            Text("${library.favoriteSongs.size} favoritos en este perfil · ${library.playlists.size} playlists compartidas",
                style = MaterialTheme.typography.bodyMedium, color = LocalAmberPalette.current.muted)
        }
        item {
            ProfileAction("Ver mis favoritos", Icons.Filled.Favorite, onOpenFavorites)
            ProfileAction("Personalizar mi tema", Icons.Filled.Palette, onOpenThemeEditor)
            ProfileAction("Cambiar foto", Icons.Filled.Image) { avatarPicker.launch(arrayOf("image/*")) }
            ProfileAction("Exportar perfil", Icons.Filled.FileUpload) {
                exportPicker.launch("AmberMusic_${active.name.replace(" ", "_")}.json")
            }
            ProfileAction("Importar perfil", Icons.Filled.FileDownload) { importPicker.launch(arrayOf("application/json", "text/plain")) }
            if (active.id != "default") ProfileAction("Eliminar este perfil", Icons.Filled.Delete) { deleteDialog = true }
        }
        item { Text("La biblioteca y las playlists existentes se comparten. Los perfiles adicionales tienen favoritos y apariencia independientes. No se envían datos a Internet.",
            color = LocalAmberPalette.current.muted, style = MaterialTheme.typography.bodySmall) }
    }
    if (createDialog || renameDialog) {
        AlertDialog(onDismissRequest = { createDialog = false; renameDialog = false },
            title = { Text(if (createDialog) "Nuevo perfil" else "Renombrar perfil") },
            text = { OutlinedTextField(value = editName, onValueChange = { editName = it.take(32) },
                label = { Text("Nombre") }, singleLine = true) },
            confirmButton = { TextButton(onClick = {
                val ok = if (createDialog) store.add(editName) else store.rename(active.id, editName)
                if (!ok) info = "Nombre inválido o demasiados perfiles"
                createDialog = false; renameDialog = false
            }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { createDialog = false; renameDialog = false }) { Text("Cancelar") } })
    }
    if (deleteDialog) AlertDialog(onDismissRequest = { deleteDialog = false },
        title = { Text("Eliminar perfil") },
        text = { Text("Se borrarán sus favoritos y apariencia propios. No se eliminará ningún archivo musical.") },
        confirmButton = { TextButton(onClick = { store.delete(active.id); deleteDialog = false }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("Cancelar") } })
    info?.let { message -> AlertDialog(onDismissRequest = { info = null }, title = { Text("Perfiles") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = { info = null }) { Text("Aceptar") } }) }
}

@Composable
private fun ProfileAvatar(uri: String?, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(LocalAmberPalette.current.surfaceRaised), contentAlignment = Alignment.Center) {
        if (!uri.isNullOrBlank()) AsyncImage(model = uri, contentDescription = "Foto del perfil",
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(CircleShape))
        else Icon(Icons.Filled.AccountCircle, contentDescription = "Perfil", tint = LocalAmberAccent.current,
            modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun ProfileAction(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).clickable(onClick = onClick)
        .padding(vertical = 14.dp, horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = LocalAmberAccent.current)
        Spacer(Modifier.width(15.dp))
        Text(title, color = LocalAmberPalette.current.foreground, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, null, tint = LocalAmberPalette.current.muted)
    }
}
