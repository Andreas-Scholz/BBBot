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

    /** The points for clearing [lines] lines with the piece that makes the combo [comboLength] long. */
    fun clear(lines: Int, comboLength: Int, emptiedBoard: Boolean): Long =
        Math.round(worth(lines) * step(comboLength) * comboLength) + if (emptiedBoard) EMPTIED_BOARD else 0
}
