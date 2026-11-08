package dev.liox.ambermusic.ui

import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.liox.ambermusic.viewmodel.MusicViewModel
import kotlin.math.roundToInt

/* Responsive settings: no long help text and no low-contrast palette labels. */
@Composable
internal fun SettingsHomeV21(onNavigate: (Int) -> Unit) {
    val palette = LocalAmberPalette.current
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 17.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 108.dp, top = 4.dp)
    ) {
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
                    .background(palette.surfaceRaised)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("AmberMusic", color = palette.accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text("Panel de personalización", color = palette.foreground, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Accede más rápido a apariencia, audio, rendimiento y tus perfiles locales.", color = palette.muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item { Text("Accesos principales", color = palette.foreground, fontWeight = FontWeight.SemiBold) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingTileV21("Apariencia", Icons.Filled.Palette, "Temas y portadas", Modifier.weight(1f)) { onNavigate(7) }
                SettingTileV21("Audio", Icons.Filled.Headphones, "Crossfade y ReplayGain", Modifier.weight(1f)) { onNavigate(9) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingTileV21("Rendimiento", Icons.Filled.Speed, "HyperOS y energía", Modifier.weight(1f)) { onNavigate(12) }
                SettingTileV21("Perfiles", Icons.Filled.AccountCircle, "Favoritos y avatar", Modifier.weight(1f)) { onNavigate(14) }
            }
        }
        item { Text("Más opciones", color = palette.foreground, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp)) }
        item { SettingDestinationV21("Editor de temas", "Personaliza colores y fondos", Icons.Filled.ColorLens) { onNavigate(15) } }
        item { SettingDestinationV21("Reproducción", "Continuidad y colas", Icons.Filled.QueueMusic) { onNavigate(11) } }
        item { SettingDestinationV21("Biblioteca", "Carpetas, filtros y escaneo", Icons.Filled.FolderOpen) { onNavigate(6) } }
        item { SettingDestinationV21("Duplicados", "Copias ocultas sin borrar archivos", Icons.Filled.ContentCopy) { onNavigate(5) } }
        item { SettingDestinationV21("Datos y respaldo", "Exportar y recuperar ajustes", Icons.Filled.CloudDownload) { onNavigate(13) } }
        item { Spacer(Modifier.height(5.dp)); Text("AmberMusic · v2.3.0-beta1", color = palette.muted,
            modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun SettingTileV21(title: String, icon: ImageVector, summary: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val palette = LocalAmberPalette.current
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(palette.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(palette.surfaceRaised), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = palette.accent)
        }
        Text(title, color = palette.foreground, fontWeight = FontWeight.SemiBold)
        Text(summary, color = palette.muted, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SettingDestinationV21(title: String, summary: String, icon: ImageVector, onClick: () -> Unit) {
    val palette = LocalAmberPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(palette.surfaceRaised).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, null, tint = palette.accent, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = palette.foreground, fontWeight = FontWeight.SemiBold)
            Text(summary, color = palette.muted, style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = palette.muted)
    }
}

@Composable
private fun SettingLabelV21(value: String) {
    Text(value, color = LocalAmberPalette.current.foreground, fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 10.dp))
}

@Composable
internal fun AppearanceSettingsV21(viewModel: MusicViewModel) {
    val selected by viewModel.theme.collectAsState()
    val style by viewModel.visualStyle.collectAsState()
    val density by viewModel.density.collectAsState()
    val reduceMotion by viewModel.reduceMotion.collectAsState()
    val cover by viewModel.fitCover.collectAsState()
    val immersive by viewModel.immersive.collectAsState()
    val palette = LocalAmberPalette.current
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)) {
        item { SettingLabelV21("Estilo") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    listOf("amoled" to "AMOLED", "immersive" to "Inmersivo"),
                    listOf("cards" to "Tarjetas", "light" to "Claro")
                ).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { (id, label) ->
                            val preview = AmberPalettes.styled(AmberPalettes.fromId(selected), id)
                            Column(Modifier.weight(1f)
                                .clip(RoundedCornerShape(15.dp))
                                .border(if (id == style) 2.dp else 1.dp,
                                    if (id == style) palette.accent else palette.muted.copy(alpha = .35f),
                                    RoundedCornerShape(15.dp))
                                .clickable { viewModel.setVisualStyle(id) }
                                .background(preview.background).padding(11.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.size(width = 24.dp, height = 24.dp)
                                        .clip(RoundedCornerShape(5.dp)).background(preview.accent))
                                    Box(Modifier.weight(1f).height(24.dp)
                                        .clip(RoundedCornerShape(5.dp)).background(preview.surfaceRaised))
                                }
                                Text(label, color = preview.foreground, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        item { SettingLabelV21("Densidad") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("comfortable" to "Cómoda", "compact" to "Compacta").forEach { (id, label) ->
                    FilterChip(selected = density == id, onClick = { viewModel.setDensity(id) }, label = { Text(label) })
                }
            }
        }
        item { SettingLabelV21("Portada y movimiento") }
        item { SettingSwitchV21("Portada completa", cover, viewModel::setFitCover) }
        item { SettingSwitchV21("Fondo inmersivo", immersive, viewModel::setImmersive) }
        item { SettingSwitchV21("Reducir animaciones", reduceMotion, viewModel::setReduceMotion) }
        item { SettingLabelV21("Acento") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AmberPalettes.all.chunked(4).forEach { group ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        group.forEach { option ->
                            Box(
                                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(14.dp))
                                    .background(option.surfaceRaised)
                                    .border(if (selected == option.id) 2.dp else 1.dp,
                                        if (selected == option.id) option.accent else option.accent.copy(alpha = .4f),
                                        RoundedCornerShape(14.dp))
                                    .clickable { viewModel.setTheme(option.id) }, contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Box(Modifier.size(18.dp).clip(RoundedCornerShape(9.dp)).background(option.accent))
                                    if (selected == option.id) Icon(Icons.Filled.Check, "Seleccionado",
                                        tint = option.accent, modifier = Modifier.size(17.dp))
                                }
                            }
                        }
                        repeat(4 - group.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchV21(title: String, enabled: Boolean, onChanged: (Boolean) -> Unit) {
    val palette = LocalAmberPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = palette.foreground, modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge)
        Switch(checked = enabled, onCheckedChange = onChanged)
    }
}

@Composable
internal fun AudioSettingsV21(viewModel: MusicViewModel) {
    val crossfade by viewModel.crossfadeSeconds.collectAsState()
    val gain by viewModel.replayGain.collectAsState()
    val offload by viewModel.offload.collectAsState()
    val smooth by viewModel.smoothTransition.collectAsState()
    val currentRoute by viewModel.currentRoute.collectAsState()
    val routePresets by viewModel.routePresets.collectAsState()
    val autoProfile by viewModel.autoOutputProfiles.collectAsState()
    val palette = LocalAmberPalette.current
    val context = LocalContext.current
    val sessionId by viewModel.audioSessionId.collectAsState()
    val currentSong by viewModel.playerUiState.collectAsState()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 110.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SettingLabelV21("Crossfade") }
                Text(if (crossfade == 0) "Off" else "${crossfade} s",
                    color = palette.accent, fontWeight = FontWeight.Bold)
            }
            Slider(value = crossfade.toFloat(), onValueChange = { viewModel.setCrossfadeSeconds(it.roundToInt()) },
                valueRange = 0f..10f, steps = 9)
            if (crossfade > 0) Text("Dos pistas se mezclan al final. Desactivar ahorra batería.",
                color = palette.muted, style = MaterialTheme.typography.bodySmall)
        }
        item { SettingSwitchV21("Entrada suave", smooth, viewModel::setSmoothTransitions) }
        item { SettingLabelV21("ReplayGain") }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("off" to "Off", "track" to "Pista", "album" to "Álbum").forEach { (id, label) ->
                    FilterChip(selected = gain == id, onClick = { viewModel.setReplayGain(id) }, label = { Text(label) })
                }
            }
        }
        item { SettingLabelV21("Ruta de señal") }
        item {
            val song = currentSong.song
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(palette.surfaceRaised).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Archivo", color = palette.accent, fontWeight = FontWeight.SemiBold)
                Text(song?.let { track ->
                    listOfNotNull(
                        track.format.uppercase(),
                        track.sampleRateHz?.let { "${it / 1000.0} kHz" },
                        track.bitDepth?.let { "$it bits" },
                        track.channelCount?.let { "$it canales" }
                    ).joinToString(" · ")
                } ?: "Selecciona una canción", color = palette.foreground)
                Text("Decodificador: Media3 / ExoPlayer (ruta efectiva pendiente de verificar)",
                    color = palette.muted, style = MaterialTheme.typography.bodySmall)
                Text("Procesado: ReplayGain ${if (gain == "off") "desactivado" else gain}",
                    color = palette.muted, style = MaterialTheme.typography.bodySmall)
                Text("Salida reportada: ${currentRoute.name}", color = palette.foreground)
                Text("Frecuencia real de salida / bit-perfect: no verificable sin diagnóstico del dispositivo.",
                    color = palette.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        item { SettingSwitchV21("Audio offload", offload, viewModel::setOffload) }
        item { Text(if (crossfade > 0) "Offload se suspende durante el crossfade." else
            "Offload se usa cuando el teléfono y el formato lo permiten.",
            color = palette.muted, style = MaterialTheme.typography.bodySmall) }
        item { SettingLabelV21("Dispositivo") }
        item { Text(currentRoute.name, color = palette.muted) }
        item { SettingSwitchV21("ReplayGain por salida", autoProfile, viewModel::setAutoOutputProfiles) }
        if (autoProfile) item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("off" to "Off", "track" to "Pista", "album" to "Álbum").forEach { (id, label) ->
                    FilterChip(selected = routePresets[currentRoute.name] == id,
                        onClick = { viewModel.setRouteReplayGain(currentRoute.name, id) },
                        label = { Text(label) })
                }
            }
        }
        item {
            SettingDestinationV21("Ecualizador del sistema", "Abrir controles de audio del teléfono",
                Icons.Filled.Equalizer) {
                val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                }
                runCatching { context.startActivity(intent) }.onFailure {
                    Toast.makeText(context, "No hay ecualizador disponible", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

@Composable
internal fun PlaybackSettingsV21(viewModel: MusicViewModel) {
    val queues by viewModel.namedQueues.collectAsState()
    val restore by viewModel.restoreSession.collectAsState()
    val previous by viewModel.previousBehavior.collectAsState()
    val muted = LocalAmberPalette.current.muted
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 100.dp)) {
        item { SettingLabelV21("Continuidad") }
        item { SettingSwitchV21("Restaurar última sesión", restore, viewModel::setRestoreSession) }
        item { SettingLabelV21("Botón Anterior") }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("restart" to "Reiniciar pista", "previous" to "Pista anterior").forEach { (id, label) ->
                    FilterChip(selected = previous == id, onClick = { viewModel.setPreviousBehavior(id) },
                        label = { Text(label) })
                }
            }
        }
        item { SettingLabelV21("Colas guardadas") }
        item { Text(if (queues.isEmpty()) "Sin colas guardadas" else queues.joinToString(" · "), color = muted) }
        item { Text("Guarda o recupera colas desde el menú del reproductor.",
            color = muted, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
internal fun PerformanceSettingsV21(viewModel: MusicViewModel) {
    val context = LocalContext.current
    val profile by viewModel.performanceProfile.collectAsState()
    val palette = LocalAmberPalette.current
    var reading by remember { mutableStateOf(dev.liox.ambermusic.diagnostics.RedmiDiagnostics.snapshot(context)) }
    fun openSettings(action: String) {
        val intent = Intent(action).apply {
            if (action == android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
                putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:${context.packageName}")))
        }
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 112.dp)) {
        item {
            Text("${reading.model} · Android ${reading.androidVersion}", color = palette.muted,
                style = MaterialTheme.typography.bodySmall)
            Text("Fiabilidad en HyperOS", color = palette.foreground,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item {
            val passed = listOf(reading.notificationPermission, reading.batteryUnrestricted == true).count { it }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(palette.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                Text("$passed de 2 permisos verificables", color = palette.foreground,
                    fontWeight = FontWeight.Bold)
                Text("Inicio automático y fijar en Recientes necesitan comprobación manual: Android no ofrece un estado fiable para ambas opciones de HyperOS.",
                    color = palette.muted, style = MaterialTheme.typography.bodySmall)
                HealthStatusRow("Notificaciones de medios", reading.notificationPermission,
                    "Controles en pantalla de bloqueo") {
                    openSettings(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                }
                HealthStatusRow("Batería sin restricciones", reading.batteryUnrestricted == true,
                    "Puede ayudar a evitar cierres del reproductor") {
                    openSettings(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                }
                Text("Inicio automático: comprobar en Ajustes > Aplicaciones > Permisos > Inicio automático.",
                    color = palette.muted, style = MaterialTheme.typography.bodySmall)
                Text("Fijar en recientes: comprobar manualmente en la pantalla de aplicaciones recientes.",
                    color = palette.muted, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { reading = dev.liox.ambermusic.diagnostics.RedmiDiagnostics.snapshot(context) }) {
                    Text("Actualizar comprobaciones")
                }
            }
        }
        item { SettingLabelV21("Perfil de energía") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("saver" to "Ahorro", "balanced" to "Equilibrado", "fluid" to "Máximo")
                    .forEach { (key, label) ->
                        FilterChip(selected = profile == key,
                            onClick = { viewModel.setPerformanceProfile(key) },
                            label = { Text(label, maxLines = 1) },
                            modifier = Modifier.weight(1f))
                    }
            }
            Text("El perfil Máximo no fuerza 120 Hz globales; HyperOS conserva el refresco adaptativo.",
                color = palette.muted, style = MaterialTheme.typography.bodySmall)
        }
        item { SettingLabelV21("Métricas comprobables") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MetricCard("RAM de la app", reading.ownProcessPssMb?.let { "$it MB" } ?: "No disponible",
                    Modifier.weight(1f))
                MetricCard("CPU", "No medida", Modifier.weight(1f))
                MetricCard("Jank", "No medido", Modifier.weight(1f))
            }
            Text("RAM es una lectura puntual PSS del proceso. CPU, fotogramas y batería requieren herramientas de medición externa; nunca mostramos cifras de ejemplo como reales.",
                color = palette.muted, style = MaterialTheme.typography.bodySmall)
        }
        item { SettingLabelV21("Hyper Island") }
        item {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(palette.surface).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Integración estándar Media3", color = palette.foreground,
                    fontWeight = FontWeight.Bold)
                Text("La música usa una sesión multimedia y notificación compatibles con Android. La aparición de la cápsula depende de HyperOS; no hay API pública de Xiaomi validada para activarla directamente.",
                    color = palette.muted, style = MaterialTheme.typography.bodySmall)
                Text("Comprueba mientras suena una canción: pantalla encendida, otra aplicación y pantalla bloqueada.",
                    color = palette.muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun HealthStatusRow(title: String, ok: Boolean, description: String, configure: () -> Unit) {
    val palette = LocalAmberPalette.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (ok) Color(0xFF7DD3A8) else Color(0xFFF5B971))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, color = palette.foreground, fontWeight = FontWeight.Medium)
            Text(description, color = palette.muted, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = configure, enabled = !ok) { Text(if (ok) "Listo" else "Configurar") }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    val palette = LocalAmberPalette.current
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(palette.surface).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = palette.muted, style = MaterialTheme.typography.labelSmall)
        Text(value, color = palette.foreground, style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun DataSettingsV21(onExport: () -> Unit, onImport: () -> Unit, onSupport: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp), contentPadding = PaddingValues(bottom = 110.dp)) {
        item { SettingLabelV21("Respaldo local") }
        item { SettingDestinationV21("Exportar respaldo", "Guardar datos en JSON", Icons.Filled.FileUpload, onExport) }
        item { SettingDestinationV21("Restaurar respaldo", "Asociar canciones disponibles", Icons.Filled.FileDownload, onImport) }
        item { SettingDestinationV21("Apoyar el proyecto", "Donación opcional", Icons.Filled.FavoriteBorder, onSupport) }
    }
}
