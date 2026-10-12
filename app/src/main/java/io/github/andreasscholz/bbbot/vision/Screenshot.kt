package io.github.andreasscholz.bbbot.vision

/** A captured screen as ARGB pixels, row by row. Plain Kotlin so recognition can be unit tested. */
class Screenshot(val width: Int, val height: Int, val pixels: IntArray) {

    init {
        require(pixels.size == width * height) { "Expected ${width * height} pixels, got ${pixels.size}" }
    }

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x]

    /**
     * Paints [area] over, every row with the color next to the area in that row. That takes
     * something lying on top of the game, the bot's own panel, out of the picture.
     */
    fun paintOver(area: ScreenArea) {
        val left = area.left.coerceIn(0, width - 1)
        val right = area.right.coerceIn(0, width - 1)
        // From the left of the area, or from its right where it reaches the left edge.
        val from = if (left >= 2 * BESIDE) left - BESIDE else minOf(width - 1, right + BESIDE)
        for (y in area.top.coerceAtLeast(0)..area.bottom.coerceAtMost(height - 1)) {
            pixels.fill(pixels[y * width + from], y * width + left, y * width + right + 1)
        }
    }

    private companion object {
        const val BESIDE = 4
    }
}

/** A rectangle on the screen, in pixels, both ends included. */
data class ScreenArea(val left: Int, val top: Int, val right: Int, val bottom: Int)
