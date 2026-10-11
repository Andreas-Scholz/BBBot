package io.github.andreasscholz.bbbot.game

import io.github.andreasscholz.bbbot.game.HeuristicEvaluator.Companion.COMBO_LENGTH

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
 *
 * Every sequence is rated quickly by the board it ends with and by what happened on the way:
 * lines cleared, and whether the combo survived. The best few are then compared once more by
 * what they have to expect from the next piece, which takes too long to work out for all.
 */
class Solver(private val evaluator: HeuristicEvaluator = HeuristicEvaluator()) {

    /**
     * [piecesSinceClear] is the number of pieces placed since a line was last cleared. The game
     * ends a combo with the third such piece, and long combos are where the points are.
     */
    fun solve(board: Board, pieces: List<Piece>, piecesSinceClear: Int = 0): Plan {
        require(pieces.size <= MAX_PIECES) { "At most $MAX_PIECES pieces" }
        val search = Search(pieces)
        search.run(
            bits = board.bits,
            remaining = (1 shl pieces.size) - 1,
            depth = 0,
            reward = 0.0,
            lines = 0,
            gap = piecesSinceClear.coerceIn(0, COMBO_LENGTH),
        )
        return search.result(board)
    }

    private class Candidate(val value: Double, val path: IntArray, val bits: Long, val lines: Int, val gap: Int)

    private inner class Search(private val pieces: List<Piece>) {
        private val masks = LongArray(pieces.size) { pieces[it].mask }
        private val widths = IntArray(pieces.size) { pieces[it].width }
        private val heights = IntArray(pieces.size) { pieces[it].height }

        private val limit = evaluator.weights.candidates

        // A step is stored as pieceIndex * 64 + row * 8 + col.
        private val path = IntArray(pieces.size)

        /** The best sequences found so far, the best first. */
        private val candidates = ArrayList<Candidate>(limit + 1)

        fun run(bits: Long, remaining: Int, depth: Int, reward: Double, lines: Int, gap: Int) {
            if (remaining == 0) {
                offer(reward + evaluator.evaluate(bits), depth, bits, lines, gap)
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
                        // Once the combo is gone there is nothing left to lose until the next clear.
                        val nextGap = if (cleared > 0) 0 else minOf(gap + 1, COMBO_LENGTH)
                        val combo = if (cleared == 0 && gap < COMBO_LENGTH) evaluator.comboReward(nextGap) else 0.0
                        path[depth] = index * CELLS + row * Board.SIZE + col
                        run(
                            bits = filled and full.inv(),
                            remaining = remaining and (1 shl index).inv(),
                            depth = depth + 1,
                            reward = reward + evaluator.lineReward(cleared) + combo,
                            lines = lines + cleared,
                            gap = nextGap,
                        )
                    }
                }
            }
            if (!placedAny) {
                // Nothing fits any more: the game is lost here, so only a longer sequence beats this.
                val value = reward + evaluator.evaluate(bits) - STUCK_PENALTY * remaining.countOneBits()
                offer(value, depth, bits, lines, gap)
            }
        }

        /** Whether an earlier piece that is still available has the same shape, so trying [index] adds nothing. */
        private fun isRepeated(index: Int, remaining: Int): Boolean {
            for (earlier in 0 until index) {
                if (remaining and (1 shl earlier) != 0 && masks[earlier] == masks[index]) return true
            }
            return false
        }

        private fun offer(value: Double, depth: Int, bits: Long, lines: Int, gap: Int) {
            if (candidates.size == limit && value <= candidates.last().value) return
            // The same board reached in another order is the same candidate.
            val same = candidates.indexOfFirst { it.bits == bits && it.path.size == depth }
            if (same >= 0) {
                if (candidates[same].value >= value) return
                candidates.removeAt(same)
            }
            val position = candidates.indexOfFirst { it.value < value }
            candidates.add(
                if (position < 0) candidates.size else position,
                Candidate(value, path.copyOf(depth), bits, lines, gap),
            )
            if (candidates.size > limit) candidates.removeAt(candidates.lastIndex)
        }

        fun result(start: Board): Plan {
            val best = candidates.maxByOrNull { it.value + evaluator.outlook(it.bits, it.gap) }
                ?: return Plan(emptyList(), start, linesCleared = 0, complete = pieces.isEmpty())
            var board = start
            val placements = best.path.map { step ->
                val index = step / CELLS
                val row = step % CELLS / Board.SIZE
                val col = step % Board.SIZE
                board = board.place(pieces[index], row, col).board
                Placement(index, pieces[index], row, col)
            }
            return Plan(placements, board, best.lines, complete = placements.size == pieces.size)
        }
    }

    private companion object {
        const val MAX_PIECES = 3
        const val CELLS = Board.SIZE * Board.SIZE
        const val STUCK_PENALTY = 1e6
    }
}
