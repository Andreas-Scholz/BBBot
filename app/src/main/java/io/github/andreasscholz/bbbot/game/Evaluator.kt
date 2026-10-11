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
    /** For letting the combo end: the third piece in a row that clears nothing. */
    val comboBreak: Double = -80.0,
    /** Per piece placed since the last clear when the offered pieces are used up. */
    val comboGap: Double = -10.0,
    /**
     * For the share of all shapes that could clear a line with their next placement. It counts
     * double after one piece without a clear and triple after two, as the combo runs out.
     */
    val clearChance: Double = 10.0,
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

    /**
     * What the combo is worth after a piece was placed. [gap] counts the pieces placed since
     * the last clear, this one included; the combo ends with the third.
     */
    fun comboReward(gap: Int): Double = if (gap == COMBO_LENGTH) weights.comboBreak else 0.0

    /**
     * Rates how well a board that ends a round keeps the combo going: a clear is needed within
     * the next `COMBO_LENGTH - gap` pieces, and the fewer are left, the more it matters that
     * many shapes can clear something right away. Much slower than [evaluate].
     */
    fun comboOutlook(bits: Long, gap: Int): Double {
        if (gap >= COMBO_LENGTH) return 0.0
        return weights.comboGap * gap + weights.clearChance * (1 + gap) * clearChance(bits)
    }

    /** The share of all shapes that can complete a row or column of the board with one placement. */
    private fun clearChance(bits: Long): Double {
        var clearing = 0
        for (piece in PieceCatalog.all) {
            if (canClear(bits, piece)) clearing++
        }
        return clearing.toDouble() / PieceCatalog.all.size
    }

    private fun canClear(bits: Long, piece: Piece): Boolean {
        for (row in 0..SIZE - piece.height) {
            for (col in 0..SIZE - piece.width) {
                val cells = piece.maskAt(row, col)
                if (bits and cells == 0L && Board.fullLines(bits or cells) != 0L) return true
            }
        }
        return false
    }

    companion object {
        /** A combo lasts as long as fewer than this many pieces in a row clear nothing. */
        const val COMBO_LENGTH = 3

        private const val ROW_7 = ROW_0 shl (SIZE * (SIZE - 1))
        private const val COL_7 = COL_0 shl (SIZE - 1)

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
