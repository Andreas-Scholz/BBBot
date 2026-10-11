package io.github.andreasscholz.bbbot.game

/** Putting piece number [pieceIndex] of the offered pieces down with its top left corner at [row], [col]. */
data class Placement(val pieceIndex: Int, val piece: Piece, val row: Int, val col: Int)

/**
 * The placements to make, in order, and the board they lead to. [complete] is false when not
 * every offered piece could be placed, which means the game ends after these placements.
 * [clearPossible] tells whether the offered pieces could clear a line at all, in any order and
 * position, whether or not the plan does.
 */
data class Plan(
    val placements: List<Placement>,
    val board: Board,
    val linesCleared: Int,
    val complete: Boolean,
    val clearPossible: Boolean,
)

/**
 * Where the combo stands. The game keeps a combo going as long as every round, the three pieces
 * offered together, clears at least one line; a round without a clear ends it, whether or not
 * a clear was possible. That was read off traces of real games, see the README.
 *
 * [length] is the number of pieces that cleared something since the combo began, and
 * [clearedThisRound] whether a piece of the round the offered pieces belong to already did.
 */
data class Combo(val length: Int = 0, val clearedThisRound: Boolean = false)

/**
 * Finds the best way to place the offered pieces by trying every order and every position.
 * With three pieces that is at most 6 · 64³ sequences, few enough to look at all of them.
 *
 * Every sequence is rated quickly by the board it ends with and by what happened on the way:
 * lines cleared, and whether the combo survived. The best few are then compared once more by
 * what they have to expect from the next piece, which takes too long to work out for all.
 */
class Solver(private val evaluator: HeuristicEvaluator = HeuristicEvaluator()) {

    /** The offered [pieces] are what is left of the current round. */
    fun solve(board: Board, pieces: List<Piece>, combo: Combo = Combo()): Plan {
        require(pieces.size <= MAX_PIECES) { "At most $MAX_PIECES pieces" }
        val search = Search(pieces, combo, board.bits)
        search.run(board.bits, remaining = (1 shl pieces.size) - 1, depth = 0, reward = 0.0, lines = 0, clears = 0)
        return search.result(board)
    }

    /** [comboLength] is the length of the combo once the sequence is played. */
    private class Candidate(val value: Double, val path: IntArray, val bits: Long, val lines: Int, val comboLength: Int)

    private inner class Search(private val pieces: List<Piece>, private val combo: Combo, private val startBits: Long) {
        /** Whether any placement that was tried cleared a line. */
        private var clearPossible = false

        private val masks = LongArray(pieces.size) { pieces[it].mask }
        private val widths = IntArray(pieces.size) { pieces[it].width }
        private val heights = IntArray(pieces.size) { pieces[it].height }
        private val limit = evaluator.weights.candidates

        // A step is stored as pieceIndex * 64 + row * 8 + col.
        private val path = IntArray(pieces.size)

        /** The best sequences found so far, the best first. */
        private val candidates = ArrayList<Candidate>(limit + 1)

        /** [clears] counts the pieces of the sequence so far that cleared something. */
        fun run(bits: Long, remaining: Int, depth: Int, reward: Double, lines: Int, clears: Int) {
            if (remaining == 0) {
                // The round is over. Without a clear in it, the combo is too.
                val kept = combo.clearedThisRound || clears > 0
                val loss = if (kept) 0.0 else evaluator.comboWorth(combo.length)
                offer(reward + evaluator.evaluate(bits) + loss, depth, bits, lines, if (kept) combo.length + clears else 0)
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
                        if (cleared > 0) clearPossible = true
                        path[depth] = index * CELLS + row * Board.SIZE + col
                        val left = filled and full.inv()
                        val emptied = if (left == 0L) evaluator.weights.emptiedBoard else 0.0
                        run(
                            bits = left,
                            remaining = remaining and (1 shl index).inv(),
                            depth = depth + 1,
                            reward = reward + evaluator.lineReward(cleared) + emptied,
                            lines = lines + cleared,
                            clears = if (cleared > 0) clears + 1 else clears,
                        )
                    }
                }
            }
            if (!placedAny) {
                // Nothing fits any more: the game is lost here, so only a longer sequence beats this.
                val value = reward + evaluator.evaluate(bits) - STUCK_PENALTY * remaining.countOneBits()
                offer(value, depth, bits, lines, comboLength = 0)
            }
        }

        /** Whether an earlier piece that is still available has the same shape, so trying [index] adds nothing. */
        private fun isRepeated(index: Int, remaining: Int): Boolean {
            for (earlier in 0 until index) {
                if (remaining and (1 shl earlier) != 0 && masks[earlier] == masks[index]) return true
            }
            return false
        }

        private fun offer(value: Double, depth: Int, bits: Long, lines: Int, comboLength: Int) {
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
                Candidate(value, path.copyOf(depth), bits, lines, comboLength),
            )
            if (candidates.size > limit) candidates.removeAt(candidates.lastIndex)
        }

        /**
         * The candidate to play: the one with the best outlook, or, when whole rounds are tried,
         * the one of the few with the best outlook that does best against those.
         */
        private fun best(): Candidate? {
            val weights = evaluator.weights
            val rated = candidates.map { it to evaluator.outlook(it.bits, it.comboLength) }
                .sortedByDescending { (candidate, outlook) -> candidate.value + outlook.total }
            if (weights.roundCandidates <= 0) return rated.firstOrNull()?.first
            // The rounds depend on nothing but the board the decision starts from, so the same
            // decision is always made the same way.
            val sampler = RoundSampler(weights.roundSamples, seed = startBits)
            return rated.take(weights.roundCandidates)
                .maxByOrNull { (candidate, outlook) ->
                    if (candidate.path.size < pieces.size) {
                        // A candidate that does not place all pieces has lost already.
                        candidate.value + outlook.total
                    } else {
                        candidate.value + outlook.future +
                            evaluator.roundOutlook(sampler.chances(candidate.bits), candidate.comboLength)
                    }
                }?.first
        }

        fun result(start: Board): Plan {
            val best = best()
                ?: return Plan(emptyList(), start, linesCleared = 0, complete = pieces.isEmpty(), clearPossible = false)
            var board = start
            val placements = best.path.map { step ->
                val index = step / CELLS
                val row = step % CELLS / Board.SIZE
                val col = step % Board.SIZE
                board = board.place(pieces[index], row, col).board
                Placement(index, pieces[index], row, col)
            }
            return Plan(placements, board, best.lines, complete = placements.size == pieces.size, clearPossible)
        }
    }

    private companion object {
        const val MAX_PIECES = 3
        const val CELLS = Board.SIZE * Board.SIZE
        const val STUCK_PENALTY = 1e6
    }
}
