package dev.liox.ambermusic.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRulesTest {
    @Test fun stripsControlCharactersAndLimitsLength() {
        assertEquals("Hola Mundo", ProfileRules.safeName("  Hola\nMundo  "))
        assertEquals(32, ProfileRules.safeName("x".repeat(80)).length)
    }
    @Test fun rejectsEmptyNamesAndUnsafeIds() {
        assertFalse(ProfileRules.validName(" \n "))
        assertFalse(ProfileRules.validIdentifier("../../other"))
        assertTrue(ProfileRules.validIdentifier("default"))
    }
    @Test fun acceptsOnlyOpaqueRgbHex() {
        assertEquals("#F48FB1", ProfileRules.hexColorOrNull("#f48fb1"))
        assertNull(ProfileRules.hexColorOrNull("#FFF"))
        assertNull(ProfileRules.hexColorOrNull("#FFFFFF00"))
        assertNull(ProfileRules.hexColorOrNull("rgb(255,0,0)"))
    }
    @Test fun textContrastsWithVeryLightAndDarkBackground() {
        assertEquals("#17151C", ProfileRules.preferredTextColor("#FFFFFF"))
        assertEquals("#FFFFFF", ProfileRules.preferredTextColor("#000000"))
    }
}
