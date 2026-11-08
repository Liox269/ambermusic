package dev.liox.ambermusic.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Design values centralised from the mobile mockup; not a device-performance claim. */
object AmberDesignTokens {
    val backgroundAmoled = Color(0xFF000000)
    val card = Color(0xFF12101A)
    val chip = Color(0xFF16131D)
    val miniPlayer = Color(0xFF1A1722)
    val text = Color(0xFFF4F1FA)
    val secondaryText = Color(0xFFA9A3B8)
    val accent = Color(0xFFE0454F)
    val activeText = Color(0xFFFF8A92)
    val playButton = Color(0xFFFF6B73)
    val eqAccent = Color(0xFF4FC3A1)
    val performanceAccent = Color(0xFFF5B971)
    val positive = Color(0xFF7DD3A8)

    val horizontalInset = 20.dp
    val settingsInset = 16.dp
    val coverCorner = 28.dp
    val cardCorner = 24.dp
    val miniPlayerCorner = 22.dp
    val rowCorner = 12.dp
    val touchTarget = 48.dp
}

/** Duration values in milliseconds, subject to validation on a real 120 Hz panel. */
object AmberMotionTokens {
    const val expandPlayer = 350
    const val changeSong = 400
    const val changeTab = 220
    const val openAlbum = 300
    const val activeLyric = 260
    const val playPause = 180
    fun duration(base: Int, reduceMotion: Boolean) = if (reduceMotion) 0 else base
}
