package io.github.andreasscholz.bbbot.game

/**
 * What the game pays for a clear, as measured in traces of real games (see the README).
 *
 * A clear of one line earns the length of the combo, counted in pieces that cleared something
 * since it began, times a step that depends on that length. Two lines at once earn exactly
 * twice that, three lines six times, four 14.4 times (measured a dozen times), five about 20
 * times and six about 30 times (each measured once). A piece that empties the board earns a
 * fixed bonus on top, whatever the combo; nothing changes for the clears after it.
 */
object Scoring {

    /** What a piece earns on top of its clear for leaving the board empty. */
    const val EMPTIED_BOARD = 300

    private val WORTH = doubleArrayOf(0.0, 1.0, 2.0, 6.0, 14.4, 20.0, 30.0)

    /** How many single lines a clear of [lines] lines at once is worth. */
    fun worth(lines: Int): Double = WORTH[lines.coerceIn(0, WORTH.lastIndex)]

    /**
     * The points a single line earns per piece of the combo, for a combo of [comboLength] pieces
     * including the clearing one. Measured at the start of two combos of one game: 50 for the
     * first five pieces, 75 for the next five, 100 from the eleventh on, where it stayed for
     * hundreds of clears. An earlier game paid 76 for a long combo; why is not known.
     */
    fun step(comboLength: Int): Int = when {
        comboLength <= 5 -> 50
        comboLength <= 10 -> 75
        else -> 100
    }

    /**
     * What a single line earns to the point with a combo of [comboLength] pieces. On top of the
     * length times the step the game pays a little more, about a point for every three pieces.
     */
    private fun singleLine(comboLength: Int): Double = when {
        comboLength <= 5 -> comboLength * 50.0 + 1
        comboLength <= 10 -> comboLength * 75.0 + 3
        else -> comboLength * (100 + 1 / 3.0)
    }

    /**
     * The length of the combo, the clearing piece included, read off the [points] that piece
     * earned by clearing [lines] lines; null when no length fits them. This is how the bot
     * learns the combo of a game it joined in the middle. The number the game itself shows
     * after a clear is one less: it does not count the piece that began the combo.
     */
    fun comboLength(points: Long, lines: Int, emptiedBoard: Boolean): Int? {
        if (lines !in 1..3) return null
        val single = (points - if (emptiedBoard) EMPTIED_BOARD else 0) / worth(lines)
        val guess = Math.round(single / (100 + 1 / 3.0)).toInt()
        val best = ((1..10) + (guess - 1..guess + 1)).filter { it >= 1 }.minBy { Math.abs(singleLine(it) - single) }
        return best.takeIf { Math.abs(singleLine(it) - single) <= FITS }
    }

    /** How far the points for a single line may be from what a combo pays and still be taken for it. */
    private const val FITS = 15.0

    /** The points for clearing [lines] lines with the piece that makes the combo [comboLength] long. */
    fun clear(lines: Int, comboLength: Int, emptiedBoard: Boolean): Long =
        Math.round(worth(lines) * step(comboLength) * comboLength) + if (emptiedBoard) EMPTIED_BOARD else 0
}
