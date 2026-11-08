package dev.liox.ambermusic.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import dev.liox.ambermusic.ui.design.AmberDesignTokens

/** Color schemes deliberately tuned for dark OLED; user choice is persisted. */
data class AmberPalette(
    val id: String,
    val title: String,
    val accent: Color,
    val secondary: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val muted: Color,
    val background: Color = Color.Black,
    val foreground: Color = Color.White
)

object AmberPalettes {
    val all = listOf(
        AmberPalette("coral", "AmberMusic Coral", AmberDesignTokens.playButton,
            AmberDesignTokens.activeText, AmberDesignTokens.card,
            AmberDesignTokens.chip, AmberDesignTokens.secondaryText,
            AmberDesignTokens.backgroundAmoled, AmberDesignTokens.text),
        AmberPalette("blue", "Azul nocturno", Color(0xFF87C6FF), Color(0xFF7FADCE), Color(0xFF0B1626), Color(0xFF14263B), Color(0xFFABC1D6)),
        AmberPalette("aigis", "Rosa suave", Color(0xFFFFB1CB), Color(0xFFE39CB4), Color(0xFF230E1B), Color(0xFF3B1C2E), Color(0xFFF0C1CF)),
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
        AmberPalette("mint", "Menta", Color(0xFFAAFFDC), Color(0xFF8CD7B9), Color(0xFF0A201B), Color(0xFF16372E), Color(0xFFCCEEDD)),
        AmberPalette("nebula", "Nebulosa", Color(0xFFB6A1FF), Color(0xFF7897DC), Color(0xFF14112A), Color(0xFF242146), Color(0xFFC9C1F6)),
        AmberPalette("lagoon", "Laguna", Color(0xFF63E8E3), Color(0xFF8ABBCB), Color(0xFF061E25), Color(0xFF12343E), Color(0xFFB8E6E5)),
        AmberPalette("ruby", "Rubí", Color(0xFFF38EBC), Color(0xFFDD90AE), Color(0xFF24101C), Color(0xFF3B1B2B), Color(0xFFF4C6D9)),
        AmberPalette("pearl", "Perla fría", Color(0xFFD4E8FD), Color(0xFFA0B2C8), Color(0xFF0F151C), Color(0xFF1F2933), Color(0xFFD1E0F0)),
        AmberPalette("sunset", "Atardecer", Color(0xFFFFBB9D), Color(0xFFE6A7AE), Color(0xFF27141C), Color(0xFF38232E), Color(0xFFF2D0C7)),
        AmberPalette("persona", "Azul eléctrico", Color(0xFF68B9FF), Color(0xFFB1DFFF), Color(0xFF061A31), Color(0xFF102D4C), Color(0xFFC3DFFF)),
        AmberPalette("velvet", "Azul profundo", Color(0xFF8A99F4), Color(0xFFB0B9F8), Color(0xFF0A1238), Color(0xFF1A2763), Color(0xFFBEC9FF)),
        AmberPalette("cherry", "Cerezo", Color(0xFFFF86B0), Color(0xFFF6BED0), Color(0xFF2A1124), Color(0xFF42213A), Color(0xFFF3CBDD)),
        AmberPalette("onyx", "Ónix", Color(0xFFB7BFCB), Color(0xFF8B99AB), Color(0xFF0A0B0E), Color(0xFF202228), Color(0xFFD6D9E0))
    )
    fun fromId(id: String): AmberPalette = all.firstOrNull { it.id == id } ?: all.first()

    fun styled(base: AmberPalette, style: String): AmberPalette = when (style) {
        "amoled" -> base.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceRaised = Color(0xFF141414),
            foreground = Color.White
        )
        "cards" -> base.copy(background = Color(0xFF111319),
            surface = Color(0xFF1A1D26), surfaceRaised = Color(0xFF303340), foreground = Color.White)
        "light" -> base.copy(background = Color(0xFFF8F9FC), surface = Color(0xFFE9ECF3),
            surfaceRaised = Color(0xFFDCE3EF), foreground = Color(0xFF17202C),
            muted = Color(0xFF4A5A6D))
        else -> base
    }
}

val LocalAmberAccent = compositionLocalOf { AmberPalettes.all.first().accent }
val LocalAmberPalette = compositionLocalOf { AmberPalettes.all.first() }

/** Shared corner system: themes can switch between compact and rounded components. */
val LocalAmberCorners = compositionLocalOf<Dp> { 20.dp }

/** Alternate outlined/filled system icon sets with no downloaded icon packs. */
val LocalAmberIconStyle = compositionLocalOf { "filled" }
