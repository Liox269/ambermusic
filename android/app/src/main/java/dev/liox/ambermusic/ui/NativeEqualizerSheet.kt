package dev.liox.ambermusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.liox.ambermusic.audio.EqualizerProfiles
import kotlin.math.roundToInt

private val eqGreen = Color(0xFF4FC3A1)
private val eqFrequencies = listOf("60 Hz", "230 Hz", "910 Hz", "3,6 kHz", "9 kHz", "14 kHz")
private val eqPresets = listOf(
    "Plano" to listOf(0f, 0f, 0f, 0f, 0f, 0f),
    "Graves" to listOf(6f, 4f, 1f, 0f, -1f, -1f),
    "Vocal" to listOf(2f, 1f, 0f, 2f, 3f, 2f),
    "Agudos" to listOf(-1f, -1f, 0f, 2f, 4f, 5f)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeEqualizerSheet(routeName: String, bluetooth: Boolean, onDismiss: () -> Unit, onSystemEqualizer: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.applicationContext.getSharedPreferences("amber_native_equalizer", android.content.Context.MODE_PRIVATE)
    }
    val detectedRoute = EqualizerProfiles.key(routeName, bluetooth)
    var selectedRoute by remember(detectedRoute) { mutableStateOf(detectedRoute) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("enabled", false)) }
    fun readBands(key: String): List<Float> = List(6) { index -> prefs.getFloat("gain_${key}_$index", 0f) }
    var values by remember(selectedRoute) { mutableStateOf(readBands(selectedRoute)) }
    fun setBands(next: List<Float>) {
        values = next
        prefs.edit().apply {
            next.forEachIndexed { index, gain -> putFloat("gain_${selectedRoute}_$index", gain) }
        }.apply()
    }
    val bg = Color(0xFF090B0B)
    val card = Color(0xFF17141D)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = bg, contentColor = Color.White) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Ecualizador", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("Perfil de salida: ${when(selectedRoute){"speaker"->"Altavoz";"wired"->"Cable / USB";else->"Bluetooth"}}",
                        color = Color.LightGray, fontSize = 12.sp)
                }
                Switch(checked = enabled, onCheckedChange = {
                    enabled = it
                    prefs.edit().putBoolean("enabled", it).apply()
                }, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF062821), checkedTrackColor = eqGreen))
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("speaker" to "Altavoz", "wired" to "Cable / USB", "bluetooth" to "Bluetooth").forEach { (key, title) ->
                    FilterChip(selected = key == selectedRoute, onClick = { selectedRoute = key; values = readBands(key) }, label = { Text(title) })
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                eqPresets.forEach { (name, preset) ->
                    FilterChip(selected = preset == values, onClick = { setBands(preset) }, label = { Text(name) })
                }
            }
            Column(Modifier.clip(RoundedCornerShape(24.dp)).background(card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                values.forEachIndexed { index, gain ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(eqFrequencies[index], modifier = Modifier.width(70.dp), fontSize = 12.sp, color = Color.LightGray)
                        Slider(value = gain, onValueChange = { next ->
                            val updated = values.toMutableList()
                            updated[index] = next.roundToInt().toFloat()
                            setBands(updated)
                        }, valueRange = -12f..12f, steps = 23, modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(thumbColor = eqGreen, activeTrackColor = eqGreen))
                        Text("${if (gain > 0) "+" else ""}${gain.roundToInt()} dB", color = eqGreen,
                            modifier = Modifier.width(51.dp), fontSize = 11.sp)
                    }
                }
            }
            Text("Las seis frecuencias son controles de perfil. Android las adapta a las bandas físicas que ofrezca el dispositivo. La disponibilidad del efecto depende de la ruta de audio.",
                color = Color.LightGray, fontSize = 12.sp)
            Text("Con Bluetooth y HyperOS puede haber efectos adicionales del sistema. Si notas doble procesado, desactiva sus efectos externos.",
                color = Color.LightGray, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSystemEqualizer) { Text("Efectos del sistema", color = eqGreen) }
                TextButton(onClick = onDismiss) { Text("Cerrar", color = eqGreen) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
