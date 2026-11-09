package dev.liox.ambermusic.settings

/** Pure rule used for optional automatic per-device ReplayGain modes. */
object OutputGainPolicy {
    fun resolve(globalMode: String, enabled: Boolean, outputName: String,
        devicePresets: Map<String, String>): String {
        val chosen = if (enabled) devicePresets[outputName] ?: globalMode else globalMode
        return chosen.takeIf { it in setOf("off", "track", "album") } ?: "off"
    }
}
