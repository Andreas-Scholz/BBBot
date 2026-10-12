package io.github.andreasscholz.bbbot.vision

/**
 * Recognizes the game's main menu, which comes up when the game was started anew: three wide
 * buttons below each other on a blue screen, orange, green and purple. The green one, "Classic",
 * goes on with the game that was being played.
 *
 * It is told by those colors in those places and by nothing else, so that nothing but this one
 * screen is ever taken for it.
 */
object MainMenu {

    /** Where to tap to go on with the classic game, or null if [shot] does not show the main menu. */
    fun classicButton(shot: Screenshot): Pair<Int, Int>? {
        val isMenu = row(shot, ADVENTURE_Y, ::isOrange) && row(shot, CLASSIC_Y, ::isGreen) && row(shot, MORE_Y, ::isPurple) &&
            BETWEEN_Y.all { y -> row(shot, y, ::isBlue) } &&
            listOf(ADVENTURE_Y, CLASSIC_Y, MORE_Y).all { y -> isBlue(at(shot, SIDE_X, y)) && isBlue(at(shot, 1 - SIDE_X, y)) }
        return if (isMenu) (shot.width / 2) to (shot.height * TAP_Y).toInt() else null
    }

    /** Whether nearly all of the stretch a button takes up at the height [y] has the color [test] looks for. */
    private fun row(shot: Screenshot, y: Float, test: (Int) -> Boolean): Boolean {
        var matching = 0
        for (step in 0 until SAMPLES) {
            val x = BUTTON_LEFT + (BUTTON_RIGHT - BUTTON_LEFT) * step / (SAMPLES - 1)
            if (test(at(shot, x, y))) matching++
        }
        return matching >= SAMPLES * 9 / 10
    }

    private fun at(shot: Screenshot, x: Float, y: Float) =
        shot[(shot.width * x).toInt().coerceIn(0, shot.width - 1), (shot.height * y).toInt().coerceIn(0, shot.height - 1)]

    private fun red(pixel: Int) = pixel shr 16 and 0xFF
    private fun green(pixel: Int) = pixel shr 8 and 0xFF
    private fun blue(pixel: Int) = pixel and 0xFF

    private fun isOrange(pixel: Int) = red(pixel) > 200 && green(pixel) in 101..199 && blue(pixel) < 100
    private fun isGreen(pixel: Int) = green(pixel) > 150 && red(pixel) < 110 && blue(pixel) > 90
    private fun isPurple(pixel: Int) = red(pixel) > 130 && blue(pixel) > 170 && green(pixel) < 140
    private fun isBlue(pixel: Int) = blue(pixel) > 150 && red(pixel) < 90

    // Where the buttons are, as fractions of the screen. The heights are below the buttons'
    // labels, where they are of one color from side to side.
    private const val BUTTON_LEFT = 0.26f
    private const val BUTTON_RIGHT = 0.74f
    private const val SIDE_X = 0.06f
    private const val ADVENTURE_Y = 0.5625f
    private const val CLASSIC_Y = 0.656f
    private const val MORE_Y = 0.7396f
    private val BETWEEN_Y = listOf(0.583f, 0.671f, 0.754f)
    private const val TAP_Y = 0.63f
    private const val SAMPLES = 40
}
