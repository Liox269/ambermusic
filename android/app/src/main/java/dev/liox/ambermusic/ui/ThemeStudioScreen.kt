package dev.liox.ambermusic.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.liox.ambermusic.profiles.ProfileRules
import dev.liox.ambermusic.settings.AmberSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/** A real, offline editor: changes immediately affect the app-wide palette. */
@Composable
fun ThemeStudioScreen() {
    val settings: AmberSettings = koinInject()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val selectedStyle by settings.visualStyle.collectAsState()
    val selectedPalette by settings.theme.collectAsState()
    val accentHex by settings.accentHex.collectAsState()
    val customBackground by settings.customBackground.collectAsState()
    val wallpaperOpacity by settings.wallpaperOpacity.collectAsState()
    val corners by settings.corners.collectAsState()
    val font by settings.fontChoice.collectAsState()
    val iconStyle by settings.iconStyle.collectAsState()
    val name by settings.customThemeName.collectAsState()
    var customColor by remember(accentHex) { mutableStateOf(accentHex) }
    var info by remember { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            settings.setCustomBackground(uri.toString())
        }
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val text = settings.exportAppearance()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
                        ?: error("No se puede guardar el tema")
                }
            }.onSuccess { info = "Tema exportado" }.onFailure { info = it.localizedMessage }
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("No se puede abrir el tema")
                }
                settings.importAppearance(text)
            }.onSuccess { info = if (it) "Tema importado" else "Archivo de tema no válido" }
                .onFailure { info = it.localizedMessage }
        }
    }
    val palette = LocalAmberPalette.current
    val accent = LocalAmberAccent.current
    val round = if (corners == "square") 7.dp else 21.dp
    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = PaddingValues(bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item { Text("Vista previa en vivo", style = MaterialTheme.typography.titleMedium, color = palette.foreground) }
        item {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(round))
                .background(palette.surfaceRaised).padding(15.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("AmberMusic", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, color = palette.foreground)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.clip(CircleShape).background(accent).padding(horizontal = 14.dp, vertical = 7.dp)) {
                            Text("Canciones", color = if (selectedStyle == "light") Color.White else Color.Black)
                        }
                        Text("Álbumes", color = palette.muted)
                        Text("Artistas", color = palette.muted)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = accent,
                            modifier = Modifier.size(43.dp))
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text("Una canción", color = palette.foreground, fontWeight = FontWeight.Bold)
                            Text("Artista", color = palette.muted)
                        }
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = accent)
                    }
                }
            }
        }
        item { Text("Estilo visual", style = MaterialTheme.typography.titleMedium, color = palette.foreground) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("amoled" to "AMOLED", "immersive" to "Inmersivo", "cards" to "Tarjetas", "light" to "Claro")
                    .forEach { (id, label) ->
                        FilterChip(selected = selectedStyle == id, onClick = { settings.setVisualStyle(id) },
                            label = { Text(label, maxLines = 1) }, modifier = Modifier.weight(1f))
                    }
            }
        }
        item { Text("Paleta de acento", style = MaterialTheme.typography.titleMedium, color = palette.foreground) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                listOf("cherry", "blue", "violet", "jade", "gold", "onyx").forEach { id ->
                    val swatch = AmberPalettes.fromId(id)
                    Box(Modifier.weight(1f).size(40.dp).clip(CircleShape).background(swatch.accent)
                        .border(if (selectedPalette == id && accentHex.isBlank()) 3.dp else 1.dp,
                            if (selectedPalette == id && accentHex.isBlank()) palette.foreground else palette.muted,
                            CircleShape).clickable { settings.setTheme(id); settings.setAccentHex("") },
                        contentAlignment = Alignment.Center) {
                        if (selectedPalette == id && accentHex.isBlank()) Icon(Icons.Filled.Check, null, tint = Color.Black)
                    }
                }
            }
        }
        item {
            OutlinedTextField(value = customColor, onValueChange = { customColor = it.take(7) },
                label = { Text("Acento personalizado (#RRGGBB)") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), trailingIcon = {
                    IconButton(onClick = {
                        if (ProfileRules.hexColorOrNull(customColor) != null) settings.setAccentHex(customColor)
                        else info = "Introduce un color válido. Ejemplo: #F48FB1"
                    }) { Icon(Icons.Filled.Check, contentDescription = "Aplicar color") }
                })
        }
        item { Text("Fondo personalizado", style = MaterialTheme.typography.titleMedium, color = palette.foreground) }
        item {
            if (customBackground.isNotBlank()) {
                AsyncImage(model = customBackground, contentDescription = "Fondo seleccionado",
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(145.dp).clip(RoundedCornerShape(18.dp)))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }) {
                    Icon(Icons.Filled.Image, null); Spacer(Modifier.width(5.dp)); Text("Elegir fondo")
                }
                if (customBackground.isNotBlank()) TextButton(onClick = { settings.setCustomBackground("") }) { Text("Quitar") }
            }
        }
        if (customBackground.isNotBlank()) item {
            Text("Intensidad del fondo", color = palette.foreground)
            Slider(value = wallpaperOpacity, onValueChange = settings::setWallpaperOpacity,
                valueRange = .1f..1f)
        }
        item { Text("Tipografía", style = MaterialTheme.typography.titleMedium, color = palette.foreground) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = font == "system", onClick = { settings.setFontChoice("system") }, label = { Text("Moderna") })
                FilterChip(selected = font == "expressive", onClick = { settings.setFontChoice("expressive") }, label = { Text("Expresiva") })
            }
        }
        item { Text("Iconos", style = MaterialTheme.typography.titleMedium, color = palette.foreground) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = iconStyle == "filled", onClick = { settings.setIconStyle("filled") }, label = { Text("Sólidos") })
                FilterChip(selected = iconStyle == "outlined", onClick = { settings.setIconStyle("outlined") }, label = { Text("Lineales") })
            }
        }
        item { Text("Componentes", style = MaterialTheme.typography.titleMedium, color = palette.foreground) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = corners == "rounded", onClick = { settings.setCorners("rounded") }, label = { Text("Redondeados") })
                FilterChip(selected = corners == "square", onClick = { settings.setCorners("square") }, label = { Text("Compactos") })
            }
        }
        item { Text("Guardar y compartir", style = MaterialTheme.typography.titleMedium, color = palette.foreground) }
        item {
            OutlinedTextField(value = name, onValueChange = settings::setCustomThemeName,
                label = { Text("Nombre del tema") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(onClick = { exportPicker.launch("AmberMusic_${name.replace(" ", "_")}.json") }) {
                    Icon(Icons.Filled.FileUpload, null); Spacer(Modifier.width(6.dp)); Text("Exportar")
                }
                OutlinedButton(onClick = { importPicker.launch(arrayOf("application/json", "text/plain")) }) {
                    Icon(Icons.Filled.FileDownload, null); Spacer(Modifier.width(6.dp)); Text("Importar")
                }
            }
        }
        item { Text("Los temas JSON guardan colores y distribución. Las imágenes de fondo permanecen en el dispositivo; no se envían ni se incluyen en el JSON.",
            color = palette.muted, style = MaterialTheme.typography.bodySmall) }
    }
    info?.let { message -> AlertDialog(onDismissRequest = { info = null }, title = { Text("Temas") },
        text = { Text(message) }, confirmButton = { TextButton(onClick = { info = null }) { Text("Aceptar") } }) }
}
