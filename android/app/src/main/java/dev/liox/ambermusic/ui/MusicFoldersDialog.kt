package dev.liox.ambermusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Folder paths originate from MediaStore.RELATIVE_PATH, not from raw filesystem access. */
@Composable
fun MusicFoldersDialog(
    current: Set<String>,
    available: List<String>,
    loading: Boolean,
    onSave: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(current) { mutableStateOf(current + "Music/") }
    val choices = (available + current + "Music/").distinct().sortedWith(
        compareBy<String> { !it.equals("Music/", ignoreCase = true) }.thenBy { it.lowercase() }
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Carpetas de música") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Music y sus subcarpetas siempre se incluyen. Selecciona otras carpetas " +
                        "para añadir sus audios. No se mueve ni se borra ningún archivo.",
                    fontSize = 13.sp, color = Color.LightGray
                )
                if (loading) Text("Buscando carpetas…", fontSize = 12.sp)
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(choices, key = { it }) { path ->
                        val pinned = path.equals("Music/", ignoreCase = true) ||
                            path.startsWith("Music/", ignoreCase = true)
                        val selected = pinned || path in draft
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !pinned) {
                                draft = if (selected) draft - path else draft + path
                            }.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = { checked ->
                                    if (!pinned) draft = if (checked) draft + path else draft - path
                                },
                                enabled = !pinned
                            )
                            Column(Modifier.weight(1f)) {
                                Text(path, maxLines = 2)
                                if (pinned) Text(
                                    "Incluida automáticamente", color = Color.Gray, fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft + "Music/") }, enabled = !loading) {
                Text("Guardar y escanear")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
