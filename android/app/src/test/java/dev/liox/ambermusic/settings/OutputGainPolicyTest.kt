package dev.liox.ambermusic.settings

import org.junit.Assert.*
import org.junit.Test

class OutputGainPolicyTest {
    @Test fun disabledKeepsGlobal() {
        assertEquals("album", OutputGainPolicy.resolve("album",false,"Bluetooth",mapOf("Bluetooth" to "track")))
    }
    @Test fun specificRouteTakesPriority() {
        assertEquals("track", OutputGainPolicy.resolve("album",true,"Bluetooth",mapOf("Bluetooth" to "track")))
    }
    @Test fun unknownRouteKeepsGlobal() {
        assertEquals("album", OutputGainPolicy.resolve("album",true,"Altavoz",mapOf("Bluetooth" to "track")))
    }
}
