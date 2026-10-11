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
    val edge: Double = -2.5,
    /** Per empty cell enclosed on all four sides. */
    val hole: Double = -7.0,
    /** Per empty cell enclosed on three sides. */
    val pocket: Double = -2.0,
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
    val comboCap: Double = 6.0,
    /**
     * The chance taken for a piece after the next one to clear a line. What the next piece can
     * do is read off the board; the pieces after it can be set up for, so they are given at
     * least this chance.
     */
    val laterClear: Double = 0.3,
    /**
     * Whether [laterClear] shrinks on a board that is far from a full line. On an empty board
     * the next round can hardly clear anything, which is how combos end after the board was
     * cleared completely. 0 switches this off, 1 switches it on.
     */
    val needsNearLine: Double = 1.0,
    /**
     * How much of a board's rating is replaced by the rating of the boards one more piece
     * later, averaged over all shapes placed as well as possible; between 0 and 1.
     */
    val lookahead: Double = 1.0,
    /** How many of the best ways to place the offered pieces get that look at their future. */
    val candidates: Int = 64,
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

    /** What losing a combo of [length] clearing pieces costs; negative. */
    fun comboWorth(length: Int): Double =
        weights.comboBreak * minOf(1 + weights.comboGrowth * length, weights.comboCap)

    /**
     * Rates what a board that ends a round has to expect from the next piece, on top of
     * [evaluate]. It tries every shape in every position, which is much too slow to do for
     * every board the solver comes across.
     *
     * The combo: it ends unless one of the three pieces of the next round clears a line. The
     * share of shapes that could do so right away is the chance of the first of them, the two
     * after it get at least [Weights.laterClear], and the expected loss is the worth of the
     * combo, [comboLength] pieces long by now, times the chance of missing every time.
     *
     * The future: how good the board still is after the next piece, on average over all shapes.
     */
    fun outlook(bits: Long, comboLength: Int): Double {
        val rateBoards = weights.lookahead != 0.0
        var clearing = 0
        var fitting = 0
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
            if (clears) clearing++
            if (fits && rateBoards) {
                fitting++
                sum += best
            }
        }

        val next = clearing.toDouble() / SHAPES.size
        // The line closest to full: with four cells or fewer to go a later piece has its usual
        // chance, with all eight to go it has none.
        var fewestEmpty = SIZE
        for (i in 0 until SIZE) {
            fewestEmpty = minOf(
                fewestEmpty,
                SIZE - (bits and (ROW_0 shl (i * SIZE))).countOneBits(),
                SIZE - (bits and (COL_0 shl i)).countOneBits(),
            )
        }
        val near = ((SIZE - fewestEmpty) / 4.0).coerceAtMost(1.0)
        val later = weights.laterClear * (1 - weights.needsNearLine * (1 - near))
        var miss = 1 - next
        repeat(ROUND - 1) { miss *= 1 - maxOf(next, later) }
        var value = comboWorth(comboLength) * miss
        if (fitting > 0) value += weights.lookahead * (sum / fitting - evaluate(bits))
        return value
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
