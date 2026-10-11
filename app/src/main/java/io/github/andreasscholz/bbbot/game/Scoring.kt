package io.github.andreasscholz.bbbot.game

/**
 * What the game pays for a clear, as far as it was measured in traces of real games (see the
 * README): a clear of one line earns an amount that grows by a fixed step with every piece that
 * cleared something since the combo began. Two lines at once earn exactly twice that, three
 * lines six times. Four and more were not seen yet; they are taken to go on as 12, 20 and 30.
 */
object Scoring {

    /**
     * The points a single line earns per piece that cleared something since the combo began, as
     * measured in two traces of one game: 76.10 both times, with 80 clears within a point of it.
     * Where the number comes from is not known, and another game may have another.
     */
    const val STEP = 76.1

    private val WORTH = intArrayOf(0, 1, 2, 6, 12, 20, 30)

    /** How many single lines a clear of [lines] lines at once is worth. */
    fun worth(lines: Int): Int = WORTH[lines.coerceIn(0, WORTH.lastIndex)]
}
