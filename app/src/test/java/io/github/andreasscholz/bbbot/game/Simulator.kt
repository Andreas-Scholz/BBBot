package io.github.andreasscholz.bbbot.game

import kotlin.random.Random

data class GameResult(
    val rounds: Int,
    val piecesPlaced: Int,
    val linesCleared: Int,
    /** Points by a rough model of the game's scoring, see [Simulator]. */
    val score: Long,
    val longestCombo: Int,
)

/**
 * Plays games against randomly drawn pieces to compare solver settings.
 *
 * The combo follows the rule read off traces of the real game: it lasts as long as every round
 * of three pieces clears at least one line. The scoring is only imitated: a point per block
 * placed, and for every clear ten points per line, more for several lines at once, multiplied
 * by the length of the combo. That a clear earns more the longer the combo is, by orders of
 * magnitude in a long game, is what the traces show; the exact numbers are not known. The real
 * game also does not draw its pieces uniformly. So the results rank settings against each
 * other and say little about the points to expect on the phone.
 */
class Simulator(private val solver: Solver, private val piecesPerRound: Int = 3) {

    fun play(random: Random, maxRounds: Int): GameResult {
        var board = Board.EMPTY
        var pieces = 0
        var lines = 0
        var score = 0L
        var combo = 0
        var longestCombo = 0
        for (round in 0 until maxRounds) {
            val offered = List(piecesPerRound) { PieceCatalog.all.random(random) }
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
                    score += 10L * result.linesCleared * (result.linesCleared + 1) / 2 * combo
                }
            }
            check(board == plan.board) { "The plan does not lead to the board it promises" }
            if (!plan.complete) return GameResult(round, pieces, lines, score, longestCombo)
            if (!cleared) combo = 0
        }
        return GameResult(maxRounds, pieces, lines, score, longestCombo)
    }
}
