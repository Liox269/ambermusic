package dev.liox.ambermusic.data.scanner

import dev.liox.ambermusic.viewmodel.initialsOf
import org.junit.Assert.assertEquals
import org.junit.Test

class InitialsTest {
    @Test fun multipleNames() {
        assertEquals("KC", initialsOf("Kalman Chodakiewitz"))
        assertEquals("ES", initialsOf("Eli Shiffman"))
        assertEquals("PM", initialsOf("Petite Meller"))
    }
}
