package io.github.andreasscholz.bbbot.game

/**
 * The 8×8 playing field as a bit set: bit `row * 8 + col` is set when that cell is filled.
 * Row 0 is the top row, column 0 the left column.
 */
@JvmInline
value class Board(val bits: Long) {

    val filledCount: Int get() = bits.countOneBits()

    operator fun get(row: Int, col: Int): Boolean = bits ushr (row * SIZE + col) and 1L != 0L

    /** Whether [piece] can be put down with its top left corner at [row], [col]. */
    fun fits(piece: Piece, row: Int, col: Int): Boolean =
        row >= 0 && col >= 0 && row + piece.height <= SIZE && col + piece.width <= SIZE &&
            bits and piece.maskAt(row, col) == 0L

    fun fitsAnywhere(piece: Piece): Boolean {
        for (row in 0..SIZE - piece.height) {
            for (col in 0..SIZE - piece.width) {
                if (bits and piece.maskAt(row, col) == 0L) return true
            }
        }
        return false
    }

    /** Puts [piece] down and removes every row and column that became full. */
    fun place(piece: Piece, row: Int, col: Int): PlaceResult {
        require(fits(piece, row, col)) { "Piece does not fit at $row, $col" }
        val filled = bits or piece.maskAt(row, col)
        val lines = fullLines(filled)
        return PlaceResult(Board(filled and lines.inv()), countLines(filled))
    }

    override fun toString(): String = buildString {
        for (row in 0 until SIZE) {
            for (col in 0 until SIZE) append(if (get(row, col)) 'X' else '.')
            if (row < SIZE - 1) append('\n')
        }
    }

    companion object {
        const val SIZE = 8
        val EMPTY = Board(0L)

        internal const val ROW_0 = 0xFFL
        internal const val COL_0 = 0x0101010101010101L

        /** Reads a board from eight strings of eight characters; `.` and space are empty cells. */
        fun parse(vararg rows: String): Board {
            require(rows.size == SIZE && rows.all { it.length == SIZE }) { "Expected $SIZE rows of $SIZE cells" }
            var bits = 0L
            for (row in 0 until SIZE) {
                for (col in 0 until SIZE) {
                    if (rows[row][col] != '.' && rows[row][col] != ' ') bits = bits or (1L shl (row * SIZE + col))
                }
            }
            return Board(bits)
        }

        /** The cells of all full rows and columns of [bits]. */
        internal fun fullLines(bits: Long): Long {
            var lines = 0L
            for (i in 0 until SIZE) {
                val row = ROW_0 shl (i * SIZE)
                if (bits and row == row) lines = lines or row
                val col = COL_0 shl i
                if (bits and col == col) lines = lines or col
            }
            return lines
        }

        internal fun countLines(bits: Long): Int {
            var count = 0
            for (i in 0 until SIZE) {
                val row = ROW_0 shl (i * SIZE)
                if (bits and row == row) count++
                val col = COL_0 shl i
                if (bits and col == col) count++
            }
            return count
        }
    }
}

data class PlaceResult(val board: Board, val linesCleared: Int)
