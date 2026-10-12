package io.github.andreasscholz.bbbot.game

import kotlin.random.Random

data class GameResult(
    val rounds: Int,
    val piecesPlaced: Int,
    val linesCleared: Int,
    /** Points by a rough model of the game's scoring, see [Simulator]. */
    val score: Long,
    val longestCombo: Int,
    /** The score after each round that was played to its end. */
    val scoreByRound: List<Long> = emptyList(),
    /** The length of the combo after each of those rounds. */
    val comboByRound: List<Int> = emptyList(),
    /** The blocks on the board at the start of every round, the one the game ended in included. */
    val filledByRound: List<Int> = emptyList(),
    /** How many rounds went by without a clear. */
    val roundsWithoutClear: Int = 0,
    /** How many of those had pieces that could not have cleared anything. */
    val roundsWithoutChance: Int = 0,
)

/**
 * Plays games against pieces dealt the way the game was seen to deal them, to compare solver
 * settings.
 *
 * The combo and the scoring follow what was read off traces of the real game: a combo lasts as
 * long as every round of three pieces clears at least one line; a block placed earns a point;
 * a clear earns what [Scoring] says, which grows with the length of the combo, counted in
 * pieces that cleared something.
 *
 * How the game picks its pieces is not known, but it does not draw them blindly. Three things
 * were measured on 2100 rounds of real games, by letting the solver play the same boards with
 * the pieces the game dealt and with pieces drawn at random (see `DealLab` and the README):
 *
 * - Which shapes come up depends on how full the board is, see [PieceCatalog].
 * - Every round dealt could be placed, also on boards where one drawn round in ten could not.
 * - The pieces dealt suit the board: with them the solver cleared an eighth more lines than
 *   with drawn ones. The difference is there from about a dozen blocks on and grows with them.
 * - A round that cannot clear anything came up as often as among drawn rounds, 18 times
 *   against 15.
 *
 * [offer] imitates that. It is a stand-in that was fitted to those numbers, not the game's
 * rule. With it the simulated game keeps as many blocks on the board as the real one at every
 * length of the combo, and hardly ever ends, as the real one. What it does not reproduce: on
 * the phone a combo of 5 to 19 pieces was lost in one round of 17, here, with the same weights,
 * in one of 50.
 */
class Simulator(
    private val solver: Solver,
    private val piecesPerRound: Int = 3,
) {

    fun play(random: Random, maxRounds: Int): GameResult {
        var board = Board.EMPTY
        var pieces = 0
        var lines = 0
        var score = 0L
        var combo = 0
        var longestCombo = 0
        val scores = ArrayList<Long>()
        val combos = ArrayList<Int>()
        val filled = ArrayList<Int>()
        var withoutClear = 0
        var withoutChance = 0
        for (round in 0 until maxRounds) {
            val offered = offer(board, random)
            filled += board.filledCount
            val plan = solver.solve(board, offered, Combo(length = combo))
            var cleared = false
            for (placement in plan.placements) {
                val result = board.place(placement.piece, placement.row, placement.col)
                board = result.board
                pieces++
                score += placement.piece.cellCount
                if (result.linesCleared > 0) {
                    cleared = true
                    lines += result.linesCleared
                    combo++
                    longestCombo = maxOf(longestCombo, combo)
                    score += Scoring.clear(result.linesCleared, combo, emptiedBoard = board == Board.EMPTY)
                }
            }
            check(board == plan.board) { "The plan does not lead to the board it promises" }
            if (!plan.complete) {
                return GameResult(round, pieces, lines, score, longestCombo, scores, combos, filled, withoutClear, withoutChance)
            }
            if (!cleared) {
                combo = 0
                withoutClear++
                if (!plan.clearPossible) withoutChance++
            }
            scores += score
            combos += combo
        }
        return GameResult(maxRounds, pieces, lines, score, longestCombo, scores, combos, filled, withoutClear, withoutChance)
    }

    /**
     * A round as the game offers it. On a board with few blocks it is the first round drawn that
     * can be placed. On a fuller board it is the best of a few such rounds, where a round is
     * the better the more lines its pieces can clear and the fewer blocks they bring; but if
     * the first of them cannot clear anything, it is dealt as it is. A round that cannot be
     * placed is dealt only when none of the rounds drawn could be.
     */
    private fun offer(board: Board, random: Random): List<Piece> {
        val wanted = if (board.filledCount < HELPED_FROM) 1 else HELPED_ROUNDS
        var best: List<Piece>? = null
        var bestValue = Double.NEGATIVE_INFINITY
        var found = 0
        var offered = emptyList<Piece>()
        repeat(wanted * 2) {
            if (found == wanted) return@repeat
            offered = List(piecesPerRound) { PieceCatalog.random(random, board.filledCount) }
            if (!RoundSampler.fits(board, offered)) return@repeat
            found++
            val lines = mostLines(board.bits, offered, used = 0)
            // The game leaves a round that cannot clear anything as often as chance does.
            if (found == 1 && lines == 0) return offered
            val value = Board.SIZE * lines - BLOCK_COST * offered.sumOf { it.cellCount }
            if (value > bestValue) {
                bestValue = value
                best = offered
            }
        }
        return best ?: offered
    }

    /** The most lines the [pieces] not yet [used] can clear together, in any order and position. */
    private fun mostLines(bits: Long, pieces: List<Piece>, used: Int): Int {
        var most = 0
        for (index in pieces.indices) {
            if (used and (1 shl index) != 0) continue
            val piece = pieces[index]
            for (row in 0..Board.SIZE - piece.height) {
                for (col in 0..Board.SIZE - piece.width) {
                    val cells = piece.maskAt(row, col)
                    if (bits and cells != 0L) continue
                    val filled = bits or cells
                    val full = Board.fullLines(filled)
                    val lines = if (full == 0L) 0 else Board.countLines(filled)
                    most = maxOf(most, lines + mostLines(filled and full.inv(), pieces, used or (1 shl index)))
                }
            }
        }
        return most
    }

    private companion object {
        /** From this many blocks on the board, the game helps. */
        const val HELPED_FROM = 12

        /** How many rounds are compared then. */
        const val HELPED_ROUNDS = 5

        /** What a block of a round counts against it, in the cells of a line it can clear. */
        const val BLOCK_COST = 0.5
    }
}
