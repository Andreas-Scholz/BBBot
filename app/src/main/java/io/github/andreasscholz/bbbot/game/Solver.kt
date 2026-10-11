package io.github.andreasscholz.bbbot.game

/** Putting piece number [pieceIndex] of the offered pieces down with its top left corner at [row], [col]. */
data class Placement(val pieceIndex: Int, val piece: Piece, val row: Int, val col: Int)

/**
 * The placements to make, in order, and the board they lead to. [complete] is false when not
 * every offered piece could be placed, which means the game ends after these placements.
 */
data class Plan(
    val placements: List<Placement>,
    val board: Board,
    val linesCleared: Int,
    val complete: Boolean,
)

/**
 * Finds the best way to place the offered pieces by trying every order and every position.
 * With three pieces that is at most 6 · 64³ sequences, few enough to look at all of them.
 */
class Solver(private val evaluator: HeuristicEvaluator = HeuristicEvaluator()) {

    fun solve(board: Board, pieces: List<Piece>): Plan {
        require(pieces.size <= MAX_PIECES) { "At most $MAX_PIECES pieces" }
        val search = Search(pieces)
        search.run(board.bits, remaining = (1 shl pieces.size) - 1, depth = 0, reward = 0.0, lines = 0)
        return search.result(board)
    }

    private inner class Search(private val pieces: List<Piece>) {
        private val masks = LongArray(pieces.size) { pieces[it].mask }
        private val widths = IntArray(pieces.size) { pieces[it].width }
        private val heights = IntArray(pieces.size) { pieces[it].height }

        // A step is stored as pieceIndex * 64 + row * 8 + col.
        private val path = IntArray(pieces.size)
        private var bestPath = IntArray(0)
        private var bestValue = Double.NEGATIVE_INFINITY
        private var bestLines = 0

        fun run(bits: Long, remaining: Int, depth: Int, reward: Double, lines: Int) {
            if (remaining == 0) {
                offer(reward + evaluator.evaluate(bits), depth, lines)
                return
            }
            var placedAny = false
            for (index in pieces.indices) {
                if (remaining and (1 shl index) == 0 || isRepeated(index, remaining)) continue
                val mask = masks[index]
                for (row in 0..Board.SIZE - heights[index]) {
                    for (col in 0..Board.SIZE - widths[index]) {
                        val cells = mask shl (row * Board.SIZE + col)
                        if (bits and cells != 0L) continue
                        placedAny = true
                        val filled = bits or cells
                        val full = Board.fullLines(filled)
                        val cleared = if (full == 0L) 0 else Board.countLines(filled)
                        path[depth] = index * CELLS + row * Board.SIZE + col
                        run(
                            bits = filled and full.inv(),
                            remaining = remaining and (1 shl index).inv(),
                            depth = depth + 1,
                            reward = reward + evaluator.lineReward(cleared),
                            lines = lines + cleared,
                        )
                    }
                }
            }
            if (!placedAny) {
                // Nothing fits any more: the game is lost here, so only a longer sequence beats this.
                offer(reward + evaluator.evaluate(bits) - STUCK_PENALTY * remaining.countOneBits(), depth, lines)
            }
        }

        /** Whether an earlier piece that is still available has the same shape, so trying [index] adds nothing. */
        private fun isRepeated(index: Int, remaining: Int): Boolean {
            for (earlier in 0 until index) {
                if (remaining and (1 shl earlier) != 0 && masks[earlier] == masks[index]) return true
            }
            return false
        }

        private fun offer(value: Double, depth: Int, lines: Int) {
            if (value > bestValue) {
                bestValue = value
                bestPath = path.copyOf(depth)
                bestLines = lines
            }
        }

        fun result(start: Board): Plan {
            var board = start
            val placements = bestPath.map { step ->
                val index = step / CELLS
                val row = step % CELLS / Board.SIZE
                val col = step % Board.SIZE
                board = board.place(pieces[index], row, col).board
                Placement(index, pieces[index], row, col)
            }
            return Plan(placements, board, bestLines, complete = placements.size == pieces.size)
        }
    }

    private companion object {
        const val MAX_PIECES = 3
        const val CELLS = Board.SIZE * Board.SIZE
        const val STUCK_PENALTY = 1e6
    }
}
