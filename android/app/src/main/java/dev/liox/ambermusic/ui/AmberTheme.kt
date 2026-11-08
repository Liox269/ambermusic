package dev.liox.ambermusic.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/** Color schemes deliberately tuned for dark OLED; user choice is persisted. */
data class AmberPalette(
    val id: String,
    val title: String,
    val accent: Color,
    val secondary: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val muted: Color,
    val background: Color = Color.Black
)

object AmberPalettes {
    val all = listOf(
        AmberPalette("coral", "AmberMusic Coral", Color(0xFFFF6B73), Color(0xFFFF9AA0), Color(0xFF121017), Color(0xFF211B24), Color(0xFFB8AFBE)),
        AmberPalette("blue", "Azul nocturno", Color(0xFF87C6FF), Color(0xFF7FADCE), Color(0xFF0B1626), Color(0xFF14263B), Color(0xFFABC1D6)),
        AmberPalette("aigis", "Aigis rosa", Color(0xFFFFB1CB), Color(0xFFE39CB4), Color(0xFF230E1B), Color(0xFF3B1C2E), Color(0xFFF0C1CF)),
        AmberPalette("violet", "Violeta noche", Color(0xFFC4ABFF), Color(0xFFB59ADF), Color(0xFF191229), Color(0xFF29203C), Color(0xFFCDBCE5)),
        AmberPalette("jade", "Jade profundo", Color(0xFF75E2CD), Color(0xFF78BFB1), Color(0xFF0C211F), Color(0xFF143631), Color(0xFFB0D5CE)),
        AmberPalette("ember", "Carmesí elegante", Color(0xFFFF9E9E), Color(0xFFE79D92), Color(0xFF260F16), Color(0xFF3C1E26), Color(0xFFF2C4C1)),
        AmberPalette("silver", "Grafito", Color(0xFFD9E4F1), Color(0xFFA6B6C6), Color(0xFF141820), Color(0xFF222831), Color(0xFFC1CBD7)),
        AmberPalette("gold", "Ámbar clásico", Color(0xFFFFD181), Color(0xFFDAB77A), Color(0xFF231C11), Color(0xFF342A19), Color(0xFFE8D0A9)),
        AmberPalette("ocean", "Océano", Color(0xFF63D6FF), Color(0xFF8ABCD7), Color(0xFF071C2C), Color(0xFF133248), Color(0xFFB2D6EA)),
        AmberPalette("sakura", "Sakura", Color(0xFFFFC8DC), Color(0xFFDCA7BE), Color(0xFF21121E), Color(0xFF352134), Color(0xFFE8C8D9)),
        AmberPalette("forest", "Bosque", Color(0xFFA6E7A5), Color(0xFF8FC5A0), Color(0xFF101E16), Color(0xFF1C3425), Color(0xFFC0D9C3)),
        AmberPalette("ice", "Hielo", Color(0xFFB9F1FF), Color(0xFF99CFDA), Color(0xFF0A1A21), Color(0xFF19303A), Color(0xFFCBECF1)),
        AmberPalette("nord", "Aurora", Color(0xFF88C9C9), Color(0xFF86A6CB), Color(0xFF0A1D26), Color(0xFF173342), Color(0xFFC2DFE6)),
        AmberPalette("indigo", "Índigo", Color(0xFF9FA8FF), Color(0xFF94A5E2), Color(0xFF12152C), Color(0xFF242A4A), Color(0xFFC8CBF6)),
        AmberPalette("copper", "Cobre", Color(0xFFFFB98C), Color(0xFFDBAA93), Color(0xFF251812), Color(0xFF39271E), Color(0xFFF0CFBC)),
        AmberPalette("mint", "Menta", Color(0xFFAAFFDC), Color(0xFF8CD7B9), Color(0xFF0A201B), Color(0xFF16372E), Color(0xFFCCEEDD))
    )
    fun fromId(id: String): AmberPalette = all.firstOrNull { it.id == id } ?: all.first()
}

val LocalAmberAccent = compositionLocalOf { AmberPalettes.all.first().accent }
val LocalAmberPalette = compositionLocalOf { AmberPalettes.all.first() }
