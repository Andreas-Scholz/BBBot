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
 * The combo and the scoring follow what was read off traces of the real game: a combo lasts as
 * long as every round of three pieces clears at least one line; a block placed earns a point;
 * a clear earns what [Scoring] says, which grows with the length of the combo, counted in
 * pieces that cleared something. The pieces are drawn with the
 * frequencies counted in the real game (see [PieceCatalog]), and a round whose pieces cannot
 * all be placed is drawn again a few times, which is a guess at what the game does (see
 * [draws]). So the points to expect on the phone may still differ.
 */
class Simulator(
    private val solver: Solver,
    private val piecesPerRound: Int = 3,
    /**
     * How often a round is drawn before the game settles for one whose pieces cannot all be
     * placed; 1 means that the game does not look at the board at all.
     *
     * What is known: on 364 boards of real games, with 18 blocks on average, every round
     * offered could be placed, where one in forty drawn at random could not have been. So the
     * game does look. But it gives no guarantee: a game ended on a board with 43 blocks, where
     * two rounds in three would have fit, with a 3×3 square that had no place at all. Drawing a
     * few times and then giving up fits both; three is a guess between them.
     */
    private val draws: Int = 3,
) {

    fun play(random: Random, maxRounds: Int): GameResult {
        var board = Board.EMPTY
        var pieces = 0
        var lines = 0
        var score = 0L
        var combo = 0
        var longestCombo = 0
        for (round in 0 until maxRounds) {
            val offered = offer(board, random)
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
            if (!plan.complete) return GameResult(round, pieces, lines, score, longestCombo)
            if (!cleared) combo = 0
        }
        return GameResult(maxRounds, pieces, lines, score, longestCombo)
    }

    /** A round as the game offers it: drawn again a few times if its pieces cannot all be placed. */
    private fun offer(board: Board, random: Random): List<Piece> {
        var offered = List(piecesPerRound) { PieceCatalog.random(random) }
        repeat(draws - 1) {
            if (RoundSampler.fits(board, offered)) return offered
            offered = List(piecesPerRound) { PieceCatalog.random(random) }
        }
        return offered
    }
}
