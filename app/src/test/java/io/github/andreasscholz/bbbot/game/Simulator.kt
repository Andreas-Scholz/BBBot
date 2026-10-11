package io.github.andreasscholz.bbbot.game

import kotlin.random.Random

data class GameResult(val rounds: Int, val piecesPlaced: Int, val linesCleared: Int)

/**
 * Plays games against randomly drawn pieces to compare solver settings. The real game does not
 * draw its pieces uniformly, so the numbers only rank settings against each other.
 */
class Simulator(private val solver: Solver, private val piecesPerRound: Int = 3) {

    fun play(random: Random, maxRounds: Int): GameResult {
        var board = Board.EMPTY
        var pieces = 0
        var lines = 0
        for (round in 0 until maxRounds) {
            val offered = List(piecesPerRound) { PieceCatalog.all.random(random) }
            val plan = solver.solve(board, offered)
            board = plan.board
            pieces += plan.placements.size
            lines += plan.linesCleared
            if (!plan.complete) return GameResult(round, pieces, lines)
        }
        return GameResult(maxRounds, pieces, lines)
    }
}
