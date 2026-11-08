package dev.liox.ambermusic.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMatcherTest {
    @Test fun handlesAccentsAndCase() {
        assertTrue(SearchMatcher.matches("cancion", "Canción de Prueba"))
        assertTrue(SearchMatcher.matches("BEYONCE", "Beyoncé"))
        assertTrue(SearchMatcher.matches("jose", "José González"))
    }
    @Test fun cachedIndexMatchesDiacritics() {
        val index = SearchMatcher.makeIndex("José", "Beyoncé", "Canción")
        assertTrue(SearchMatcher.matchesIndex(SearchMatcher.normalize("BEYONCE"), index))
        assertTrue(SearchMatcher.matchesIndex(SearchMatcher.normalize("cancion"), index))
        assertFalse(SearchMatcher.matchesIndex(SearchMatcher.normalize("rock"), index))
    }
    @Test fun allowsSearchAcrossMetadataFields() {
        assertTrue(SearchMatcher.matches("rock", "Helena", "My Chemical Romance", "Rock Alternativo"))
        assertFalse(SearchMatcher.matches("jazz", "Helena", "My Chemical Romance"))
    }
}
