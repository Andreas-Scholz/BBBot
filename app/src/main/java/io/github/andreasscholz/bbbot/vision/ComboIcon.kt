package io.github.andreasscholz.bbbot.vision

import kotlin.math.abs

/**
 * The game draws a symbol behind the score while a combo is running: a heart, a diamond or
 * something else, in whatever colors the theme has. This measures how much of it is there, as
 * the share of pixels around the score that are neither background nor the white digits nor
 * their grey shadows. Without the symbol that share is about 0.01, with it 0.13 and more.
 */
object ComboIcon {

    /** Above this share the symbol is taken to be there. */
    const val PRESENT = 0.05f

    // The area around the score, as fractions of the screen.
    const val TOP = 0.15f
    const val BOTTOM = 0.215f
    private const val LEFT = 0.3f
    private const val RIGHT = 0.7f

    private const val EDGE_X = 4
    private const val BACKGROUND_DISTANCE = 90
    private const val WHITE = 170
    private const val GREY_SPREAD = 35
    private const val WHITE_SPREAD = 50

    fun share(shot: Screenshot): Float {
        val top = (shot.height * TOP).toInt()
        val bottom = (shot.height * BOTTOM).toInt()
        val left = (shot.width * LEFT).toInt()
        val right = (shot.width * RIGHT).toInt()
        var colored = 0
        var total = 0
        for (y in top until bottom step 2) {
            val background = shot[EDGE_X, y]
            for (x in left until right step 2) {
                total++
                val pixel = shot[x, y]
                val red = pixel shr 16 and 0xFF
                val green = pixel shr 8 and 0xFF
                val blue = pixel and 0xFF
                val distance = abs(red - (background shr 16 and 0xFF)) +
                    abs(green - (background shr 8 and 0xFF)) +
                    abs(blue - (background and 0xFF))
                if (distance <= BACKGROUND_DISTANCE) continue
                val low = minOf(red, green, blue)
                val spread = maxOf(red, green, blue) - low
                val white = low > WHITE && spread < WHITE_SPREAD
                if (!white && spread >= GREY_SPREAD) colored++
            }
        }
        return colored.toFloat() / total
    }
}
