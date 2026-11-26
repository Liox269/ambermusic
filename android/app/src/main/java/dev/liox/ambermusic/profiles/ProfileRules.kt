package dev.liox.ambermusic.profiles

/** Pure validation used by the UI and archive import; deliberately Android-free. */
object ProfileRules {
    const val MAX_PROFILES = 8
    fun safeName(input: String): String = input.trim().replace(Regex("[\\r\\n\\t]+"), " ").take(32)
    fun validName(input: String) = safeName(input).isNotEmpty()
    fun validIdentifier(id: String) = id.matches(Regex("[a-zA-Z0-9_-]{1,64}"))
    fun hexColorOrNull(input: String): String? {
        val value = input.trim().uppercase()
        return value.takeIf { it.matches(Regex("#[0-9A-F]{6}")) }
    }
    fun preferredTextColor(background: String): String {
        val hex = hexColorOrNull(background) ?: return "#FFFFFF"
        val r = hex.substring(1, 3).toInt(16) / 255.0
        val g = hex.substring(3, 5).toInt(16) / 255.0
        val b = hex.substring(5, 7).toInt(16) / 255.0
        fun linear(v: Double) = if (v <= .04045) v / 12.92 else Math.pow((v + .055) / 1.055, 2.4)
        val luminance = .2126 * linear(r) + .7152 * linear(g) + .0722 * linear(b)
        return if (luminance > .179) "#17151C" else "#FFFFFF"
    }
}
