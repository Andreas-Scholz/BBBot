package io.github.andreasscholz.bbbot.game

/**
 * A piece, stored like a [Board] with the shape pushed into the top left corner. Pieces cannot
 * be rotated in the game, so every orientation is a piece of its own.
 */
@JvmInline
value class Piece private constructor(val mask: Long) {

    val width: Int
        get() {
            var columns = 0L
            for (row in 0 until Board.SIZE) columns = columns or (mask ushr (row * Board.SIZE) and 0xFFL)
            return Long.SIZE_BITS - columns.countLeadingZeroBits()
        }

    val height: Int get() = (Long.SIZE_BITS - mask.countLeadingZeroBits() + Board.SIZE - 1) / Board.SIZE

    val cellCount: Int get() = mask.countOneBits()

    /** Whether the cell at [row], [col] of the bounding box belongs to the piece. */
    operator fun get(row: Int, col: Int): Boolean = mask ushr (row * Board.SIZE + col) and 1L != 0L

    /** The cells covered on the board when the top left corner is at [row], [col]. */
    fun maskAt(row: Int, col: Int): Long = mask shl (row * Board.SIZE + col)

    override fun toString(): String = buildString {
        for (row in 0 until height) {
            for (col in 0 until width) append(if (get(row, col)) 'X' else '.')
            if (row < height - 1) append('\n')
        }
    }

    companion object {
        /** Reads a piece from rows like `"XX", "X."`; `.` and space are gaps. */
        fun of(vararg rows: String): Piece {
            val cells = mutableListOf<Pair<Int, Int>>()
            for ((row, line) in rows.withIndex()) {
                for ((col, char) in line.withIndex()) {
                    if (char != '.' && char != ' ') cells += row to col
                }
            }
            return ofCells(cells)
        }

        /** Builds a piece from (row, column) cells; the position of the shape does not matter. */
        fun ofCells(cells: Collection<Pair<Int, Int>>): Piece {
            require(cells.isNotEmpty()) { "A piece needs at least one cell" }
            val top = cells.minOf { it.first }
            val left = cells.minOf { it.second }
            var mask = 0L
            for ((row, col) in cells) {
                require(row - top < Board.SIZE && col - left < Board.SIZE) { "Piece is larger than the board" }
                mask = mask or (1L shl ((row - top) * Board.SIZE + (col - left)))
            }
            return Piece(mask)
        }
    }
}
