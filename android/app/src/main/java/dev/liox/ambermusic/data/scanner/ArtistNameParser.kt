package dev.liox.ambermusic.data.scanner

import java.util.Locale

object ArtistNameParser {
    private val separators = Regex(
        """\s*(?:;|,|/|\s+&\s+|\bfeat\.?(?=\s|$)|\bft\.?(?=\s|$))\s*""",
        RegexOption.IGNORE_CASE
    )

    fun parse(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return listOf("Artista desconocido")
        val names = raw.replace('\u0000', ';')
            .split(separators)
            .map { it.trim().trim(' ', ';', ',', '&') }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
        return names.ifEmpty { listOf("Artista desconocido") }
    }
}
