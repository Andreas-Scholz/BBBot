package io.github.andreasscholz.bbbot.game

import io.github.andreasscholz.bbbot.game.Board.Companion.COL_0
import io.github.andreasscholz.bbbot.game.Board.Companion.ROW_0
import io.github.andreasscholz.bbbot.game.Board.Companion.SIZE

/**
 * How much each property of a board counts. The defaults were found by letting the solver play
 * simulated games (see `SimulatorTest`).
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
    val squareFit: Double = 4.0,
    /** Per position where a line of five still fits, up to [fitCap] positions per direction. */
    val lineFit: Double = 0.5,
    val fitCap: Int = 6,
    /** Per row or column cleared on the way to the board. */
    val line: Double = 1.0,
    /** Extra per additional line cleared by the same piece. */
    val multiLine: Double = 1.0,
)

class HeuristicEvaluator(private val weights: Weights = Weights()) {

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
        if (lines == 0) 0.0 else weights.line * lines + weights.multiLine * (lines - 1)

    private companion object {
        const val ROW_7 = ROW_0 shl (SIZE * (SIZE - 1))
        const val COL_7 = COL_0 shl (SIZE - 1)

        /** The cells that start a horizontal run of [length] cells of [cells]. */
        fun rightRun(cells: Long, length: Int): Long {
            var run = cells
            repeat(length - 1) { run = cells and (run ushr 1 and COL_7.inv()) }
            return run
        }

        /** The cells that start a vertical run of [length] cells of [cells]. */
        fun downRun(cells: Long, length: Int): Long {
            var run = cells
            repeat(length - 1) { run = cells and (run ushr SIZE) }
            return run
        }
    }
}
