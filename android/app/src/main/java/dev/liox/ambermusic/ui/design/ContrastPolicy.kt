package dev.liox.ambermusic.ui.design

import kotlin.math.pow

/** WCAG relative contrast calculations, independent of Android and Compose. */
object ContrastPolicy {
    fun luminance(argb: Int): Double {
        fun linear(component: Int): Double {
            val v = component / 255.0
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        val r = linear((argb ushr 16) and 255)
        val g = linear((argb ushr 8) and 255)
        val b = linear(argb and 255)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    fun ratio(a: Int, b: Int): Double {
        val aa = luminance(a)
        val bb = luminance(b)
        return (maxOf(aa, bb) + 0.05) / (minOf(aa, bb) + 0.05)
    }

    /** Choose black or white for at least a 4.5:1 body-text contrast ratio. */
    fun onColor(background: Int): Int {
        val white = 0xFFFFFFFF.toInt()
        val black = 0xFF000000.toInt()
        return if (ratio(black, background) >= ratio(white, background)) black else white
    }

    fun readable(background: Int, foreground: Int, largeText: Boolean = false): Boolean =
        ratio(background, foreground) >= if (largeText) 3.0 else 4.5
}
