package dev.liox.ambermusic.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
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
            Box(Modifier.fillMaxWidth().height(105.dp).clip(RoundedCornerShape(20.dp))
                .background(card).padding(12.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val middle = size.height * 0.50f
                    val plot = Path()
                    values.forEachIndexed { i, v ->
                        val x = i * size.width / (values.size - 1).coerceAtLeast(1)
                        val y = (middle - v / 12f * size.height * 0.37f)
                            .coerceIn(2f, size.height - 2f)
                        if (i == 0) plot.moveTo(x, y) else plot.lineTo(x, y)
                    }
                    drawLine(eqGreen.copy(alpha = 0.2f), Offset(0f, middle),
                        Offset(size.width, middle), strokeWidth = 1.dp.toPx())
                    drawPath(plot, eqGreen, style = Stroke(width = 2.2.dp.toPx(),
                        cap = StrokeCap.Round))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                values.forEachIndexed { index, gain ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("${if (gain > 0f) "+" else ""}${gain.roundToInt()} dB",
                            color = eqGreen, fontSize = 10.sp, maxLines = 1)
                        EqVerticalFader(gain = gain, modifier = Modifier.height(188.dp).width(42.dp)) { next ->
                            val updated = values.toMutableList()
                            updated[index] = next.roundToInt().toFloat()
                            setBands(updated)
                        }
                        Text(eqFrequencies[index].replace(" Hz", "").replace(" kHz", "k"),
                            color = Color.LightGray, fontSize = 9.sp, maxLines = 1)
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

@Composable
private fun EqVerticalFader(gain: Float, modifier: Modifier, onGain: (Float) -> Unit) {
    val currentOnGain by rememberUpdatedState(onGain)
    Canvas(modifier.pointerInput(Unit) {
        detectTapGestures { point ->
            currentOnGain((12f - point.y / size.height * 24f).coerceIn(-12f, 12f))
        }
    }.pointerInput(Unit) {
        detectVerticalDragGestures(onVerticalDrag = { change, _ ->
            change.consume()
            currentOnGain((12f - change.position.y / size.height * 24f).coerceIn(-12f, 12f))
        })
    }) {
        val x = size.width / 2f
        val top = 13.dp.toPx()
        val bottom = size.height - top
        val y = (bottom - (gain + 12f) / 24f * (bottom - top)).coerceIn(top, bottom)
        drawLine(Color(0xFF282930), Offset(x, top), Offset(x, bottom),
            strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        drawLine(eqGreen, Offset(x, y), Offset(x, bottom),
            strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(x, y))
    }
}
