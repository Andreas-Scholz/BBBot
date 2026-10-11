package io.github.andreasscholz.bbbot.game

import io.github.andreasscholz.bbbot.game.HeuristicEvaluator.Companion.COMBO_LENGTH
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
 * Plays games against randomly drawn pieces to compare solver settings. The real game does not
 * draw its pieces uniformly, and its scoring is only imitated: a point per block placed, and
 * for every clear ten points per line, more for several lines at once, multiplied by the
 * length of the combo. A combo ends with the third piece in a row that clears nothing. So the
 * numbers only rank settings against each other.
 */
class Simulator(private val solver: Solver, private val piecesPerRound: Int = 3) {

    fun play(random: Random, maxRounds: Int): GameResult {
        var board = Board.EMPTY
        var pieces = 0
        var lines = 0
        var score = 0L
        var combo = 0
        var longestCombo = 0
        var gap = 0
        for (round in 0 until maxRounds) {
            val offered = List(piecesPerRound) { PieceCatalog.all.random(random) }
            val plan = solver.solve(board, offered, gap)
            for (placement in plan.placements) {
                val result = board.place(placement.piece, placement.row, placement.col)
                board = result.board
                pieces++
                score += placement.piece.cellCount
                if (result.linesCleared > 0) {
                    lines += result.linesCleared
                    combo++
                    longestCombo = maxOf(longestCombo, combo)
                    gap = 0
                    score += 10L * result.linesCleared * (result.linesCleared + 1) / 2 * combo
                } else if (++gap >= COMBO_LENGTH) {
                    combo = 0
                }
            }
            check(board == plan.board) { "The plan does not lead to the board it promises" }
            if (!plan.complete) return GameResult(round, pieces, lines, score, longestCombo)
        }
        return GameResult(maxRounds, pieces, lines, score, longestCombo)
    }
}
