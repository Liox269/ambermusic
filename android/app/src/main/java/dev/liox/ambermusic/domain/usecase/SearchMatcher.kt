package dev.liox.ambermusic.domain.usecase

import java.text.Normalizer
import java.util.Locale

/** Locale-independent matching ("Beyonce" matches "Beyoncé", etc). */
object SearchMatcher {
    private val combiningMarks = Regex("\\p{M}+")
    private fun fold(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(combiningMarks, "")
        .lowercase(Locale.ROOT)

    fun normalize(query: String): String = fold(query.trim())

    fun makeIndex(vararg fields: String): String = fields.joinToString(" ") { fold(it) }

    fun matchesIndex(normalizedQuery: String, indexedText: String): Boolean =
        normalizedQuery.isEmpty() || indexedText.contains(normalizedQuery)

    fun matches(query: String, vararg fields: String): Boolean {
        val needle = normalize(query)
        return needle.isEmpty() || fields.any { fold(it).contains(needle) }
    }
}
