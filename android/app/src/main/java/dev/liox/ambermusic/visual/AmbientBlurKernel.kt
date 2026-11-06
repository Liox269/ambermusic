package dev.liox.ambermusic.visual

/** Pure, linear-time, separable blur used once per album image. No Android or Compose dependency. */
internal object AmbientBlurKernel {
    fun blurInPlace(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int = 3) {
        require(width > 0 && height > 0 && pixels.size == width * height)
        require(radius in 1..128 && passes in 1..5)
        val scratch = IntArray(pixels.size)
        repeat(passes) {
            boxPass(pixels, scratch, width, height, radius, horizontal = true)
            boxPass(scratch, pixels, width, height, radius, horizontal = false)
        }
    }

    private fun boxPass(
        input: IntArray, output: IntArray, width: Int, height: Int,
        radius: Int, horizontal: Boolean
    ) {
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        val diameter = radius * 2 + 1
        for (line in 0 until lines) {
            fun indexAt(pos: Int): Int {
                val clamped = pos.coerceIn(0, length - 1)
                return if (horizontal) line * width + clamped else clamped * width + line
            }
            var r = 0
            var g = 0
            var b = 0
            for (offset in -radius..radius) {
                val color = input[indexAt(offset)]
                r += (color ushr 16) and 255
                g += (color ushr 8) and 255
                b += color and 255
            }
            for (position in 0 until length) {
                output[indexAt(position)] = (0xFF shl 24) or
                    ((r / diameter) shl 16) or ((g / diameter) shl 8) or (b / diameter)
                val remove = input[indexAt(position - radius)]
                val add = input[indexAt(position + radius + 1)]
                r += ((add ushr 16) and 255) - ((remove ushr 16) and 255)
                g += ((add ushr 8) and 255) - ((remove ushr 8) and 255)
                b += (add and 255) - (remove and 255)
            }
        }
    }
}
