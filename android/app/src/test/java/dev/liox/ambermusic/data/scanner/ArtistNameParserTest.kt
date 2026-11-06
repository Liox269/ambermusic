package dev.liox.ambermusic.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistNameParserTest {
    @Test
    fun `separa tres artistas de una pista`() {
        assertEquals(
            listOf("Kalman Chodakiewitz", "Eli Shiffman", "Petite Meller"),
            ArtistNameParser.parse("Kalman Chodakiewitz, Eli Shiffman, Petite Meller")
        )
    }

    @Test
    fun `separa colaboradores con todos los delimitadores`() {
        assertEquals(
            listOf("Uno", "Dos", "Tres", "Cuatro", "Cinco", "Seis"),
            ArtistNameParser.parse("Uno; Dos / Tres & Cuatro feat. Cinco ft. Seis")
        )
    }

    @Test
    fun `elimina nombres repetidos sin distinguir mayusculas`() {
        assertEquals(listOf("Rosa Walton", "Hallie Coggins"),
            ArtistNameParser.parse("Rosa Walton, Hallie Coggins, rosa walton"))
    }

    @Test
    fun `maneja tags vacios`() {
        assertEquals(listOf("Artista desconocido"), ArtistNameParser.parse("  "))
    }
}
