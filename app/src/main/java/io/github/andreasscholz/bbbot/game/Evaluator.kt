package io.github.andreasscholz.bbbot.game

import io.github.andreasscholz.bbbot.game.Board.Companion.COL_0
import io.github.andreasscholz.bbbot.game.Board.Companion.ROW_0
import io.github.andreasscholz.bbbot.game.Board.Companion.SIZE

/**
 * How much each property of a board counts. The sets in use are the ones of [Strategy]; they
 * were found by letting the solver play simulated games (see `StrategyLab` in the tests).
 */
data class Weights(
    /** Per empty cell. */
    val empty: Double = 2.0,
    /** Per edge between a filled and an empty cell, the outer wall counting as filled. */
    val edge: Double = -3.5,
    /** Per empty cell enclosed on all four sides. */
    val hole: Double = -20.0,
    /** Per empty cell enclosed on three sides. */
    val pocket: Double = -6.0,
    /** Per position where a 3×3 square still fits, up to [fitCap] positions. */
    val squareFit: Double = 6.0,
    /** Per position where a line of five still fits, up to [fitCap] positions per direction. */
    val lineFit: Double = 0.5,
    val fitCap: Int = 6,
    /**
     * For a clear, per single line it is worth in the game: 1 for one line, 2 for two at once,
     * 6 for three at once (see [Scoring]).
     */
    val line: Double = 1.0,
    /**
     * Per piece that clears anything, however many lines. The game lengthens the combo by one
     * for every such piece, so two pieces clearing a line each do more for it than one piece
     * clearing two.
     */
    val clearingPiece: Double = 3.0,
    /**
     * For a piece that empties the board. In the game a line earned about a third more per step
     * of the combo after the board had been emptied a few times; how exactly is being measured,
     * and until then this is set to make it happen often enough to measure.
     */
    val emptiedBoard: Double = 25.0,
    /**
     * What a combo is worth when it begins, as a loss when it ends. The more a combo counts,
     * the more of the board's good shape is given up to keep clearing lines.
     */
    val comboBreak: Double = -10.0,
    /**
     * How much the worth of a combo grows with every piece that cleared something, as a
     * fraction of [comboBreak]. In the game a clear earns more the longer the combo is, so a
     * long combo is worth more than a short one.
     */
    val comboGrowth: Double = 0.05,
    /** The most a combo can be worth, as a multiple of [comboBreak]. */
    val comboCap: Double = 11.0,
    /**
     * The chance taken for a piece after the next one to clear a line. What the next piece can
     * do is read off the board; the pieces after it can be set up for, so they are given at
     * least this chance.
     */
    val laterClear: Double = 0.3,
    /**
     * How much of a board's rating is replaced by the rating of the boards one more piece
     * later, averaged over all shapes placed as well as possible; between 0 and 1.
     */
    val lookahead: Double = 1.0,
    /** How many of the best ways to place the offered pieces get that look at their future. */
    val candidates: Int = 64,
    /**
     * How many of the very best ways are tried against whole rounds drawn at random, see
     * [RoundSampler]; 0 switches that off. It is off: it helped against pieces drawn evenly and
     * did harm against pieces drawn the way the game was counted to draw them.
     */
    val roundCandidates: Int = 0,
    /** How many rounds each of them is tried against. */
    val roundSamples: Int = 128,
    /**
     * By how much the share of rounds that cannot clear anything is multiplied to get the chance
     * of losing the combo. That a round can clear a line does not mean that it is wise to, so
     * the chance of losing the combo is higher than the share of rounds that cannot.
     */
    val roundDoubt: Double = 2.0,
    /** For a next round whose pieces do not all fit, which ends the game. */
    val roundDeath: Double = -200.0,
)

class HeuristicEvaluator(val weights: Weights = Weights()) {

    /** Rates a board given as [Board.bits]; higher is better for the player. */
    fun evaluate(bits: Long): Double {
        val empty = bits.inv()

        // Neighbors of every cell, with the wall standing in outside the board.
        val above = bits shl SIZE or ROW_0
        val below = bits ushr SIZE or ROW_7
        val left = bits and COL_7.inv() shl 1 or COL_0
        val right = bits and COL_0.inv() ushr 1 or COL_7

        val edges = (empty and above).countOneBits() + (empty and below).countOneBits() +
            (empty and left).countOneBits() + (empty and right).countOneBits()
        val holes = empty and above and below and left and right
        val pockets = empty and holes.inv() and
            (above and below and (left or right) or (left and right and (above or below)))

        val square = downRun(rightRun(empty, 3), 3).countOneBits()
        val horizontalLine = rightRun(empty, 5).countOneBits()
        val verticalLine = downRun(empty, 5).countOneBits()

        return weights.empty * empty.countOneBits() +
            weights.edge * edges +
            weights.hole * holes.countOneBits() +
            weights.pocket * pockets.countOneBits() +
            weights.squareFit * minOf(square, weights.fitCap) +
            weights.lineFit * (minOf(horizontalLine, weights.fitCap) + minOf(verticalLine, weights.fitCap))
    }

    /** Reward for clearing [lines] rows and columns with one piece. */
    fun lineReward(lines: Int): Double =
        if (lines == 0) 0.0 else weights.line * Scoring.worth(lines) + weights.clearingPiece

    /**
     * Rates a board by what happened when it was tried against whole rounds: the combo is lost
     * with the share of rounds that could not clear anything, the game with the share whose
     * pieces did not fit. This takes the place of [Outlook.comboRisk], which only guesses the
     * first of the two.
     */
    fun roundOutlook(chances: RoundChances, comboLength: Int): Double =
        comboWorth(comboLength) * minOf(1.0, weights.roundDoubt * (1 - chances.clear)) +
            weights.roundDeath * (1 - chances.fit)

    /** What losing a combo of [length] clearing pieces costs; negative. */
    fun comboWorth(length: Int): Double =
        weights.comboBreak * minOf(1 + weights.comboGrowth * length, weights.comboCap)

    /**
     * What a board that ends a round has to expect from the next piece, on top of [evaluate].
     *
     * [future] is how good the board still is after the next piece, on average over all shapes.
     *
     * [comboRisk] is the expected loss of the combo: it ends unless one of the three pieces of
     * the next round clears a line. The share of shapes that could do so right away is the
     * chance of the first of them, the two after it get at least [Weights.laterClear], and the
     * loss is the worth of the combo times the chance of missing every time.
     */
    class Outlook(val future: Double, val comboRisk: Double) {
        val total: Double get() = future + comboRisk
    }

    /**
     * Works out the [Outlook] of a board with a combo of [comboLength] pieces. It tries every
     * shape in every position, which is much too slow to do for every board the solver comes
     * across.
     */
    fun outlook(bits: Long, comboLength: Int): Outlook {
        val rateBoards = weights.lookahead != 0.0
        // Shares of the shapes, weighted by how often the game hands each out.
        var clearing = 0.0
        var fitting = 0.0
        var sum = 0.0
        for (index in SHAPES.indices) {
            val mask = SHAPES[index]
            var fits = false
            var clears = false
            var best = Double.NEGATIVE_INFINITY
            for (row in 0..SIZE - SHAPE_HEIGHTS[index]) {
                for (col in 0..SIZE - SHAPE_WIDTHS[index]) {
                    val cells = mask shl (row * SIZE + col)
                    if (bits and cells != 0L) continue
                    fits = true
                    val filled = bits or cells
                    val full = Board.fullLines(filled)
                    if (full != 0L) clears = true
                    if (rateBoards) {
                        val lines = if (full == 0L) 0 else Board.countLines(filled)
                        best = maxOf(best, evaluate(filled and full.inv()) + lineReward(lines))
                    }
                }
            }
            if (clears) clearing += SHAPE_SHARES[index]
            if (fits && rateBoards) {
                fitting += SHAPE_SHARES[index]
                sum += best * SHAPE_SHARES[index]
            }
        }

        val next = clearing
        var miss = 1 - next
        repeat(ROUND - 1) { miss *= 1 - maxOf(next, weights.laterClear) }
        val future = if (fitting > 0) weights.lookahead * (sum / fitting - evaluate(bits)) else 0.0
        return Outlook(future, comboWorth(comboLength) * miss)
    }

    companion object {
        /** The number of pieces offered together. */
        const val ROUND = 3

        private const val ROW_7 = ROW_0 shl (SIZE * (SIZE - 1))
        private const val COL_7 = COL_0 shl (SIZE - 1)

        // The catalog as plain arrays, for the inner loop of [outlook].
        private val SHAPES = LongArray(PieceCatalog.all.size) { PieceCatalog.all[it].mask }
        private val SHAPE_WIDTHS = IntArray(PieceCatalog.all.size) { PieceCatalog.all[it].width }
        private val SHAPE_HEIGHTS = IntArray(PieceCatalog.all.size) { PieceCatalog.all[it].height }
        private val SHAPE_SHARES = PieceCatalog.shares

        /** The cells that start a horizontal run of [length] cells of [cells]. */
        private fun rightRun(cells: Long, length: Int): Long {
            var run = cells
            repeat(length - 1) { run = cells and (run ushr 1 and COL_7.inv()) }
            return run
        }

        /** The cells that start a vertical run of [length] cells of [cells]. */
        private fun downRun(cells: Long, length: Int): Long {
            var run = cells
            repeat(length - 1) { run = cells and (run ushr SIZE) }
            return run
        }
    }
}
