package io.github.andreasscholz.bbbot.game

import kotlin.random.Random

/**
 * The shapes the game hands out and how often, counted in traces of real games: 1161 pieces in
 * 387 rounds (see the README). The bot does not depend on this list to recognize pieces; it is
 * used to judge what a board has to expect and to play simulated games.
 *
 * The game does not draw evenly. A 3×3 square is one piece in eight, single blocks and the
 * short lines of two and three did not come up at all.
 */
object PieceCatalog {

    private val counted: List<Pair<Piece, Int>> = listOf(
        Piece.of("XXX", "XXX", "XXX") to 137,
        Piece.of("XX", "XX") to 105,
        Piece.of("X", "X", "X", "X") to 74,
        Piece.of("XXX", "XXX") to 65,
        Piece.of("XXXX") to 62,
        Piece.of("XXXXX") to 60,
        Piece.of("XX", "XX", "XX") to 54,
        Piece.of("X", "X", "X", "X", "X") to 44,
        Piece.of(".XX", "XX.") to 43,
        Piece.of("X..", "XXX") to 43,
        Piece.of("X.", "XX", "X.") to 35,
        Piece.of("X..", "X..", "XXX") to 34,
        Piece.of("..X", "..X", "XXX") to 33,
        Piece.of("X.", "XX", ".X") to 33,
        Piece.of(".X.", "XXX") to 31,
        Piece.of(".X", "XX", ".X") to 31,
        Piece.of("XXX", "..X") to 30,
        Piece.of("XX.", ".XX") to 29,
        Piece.of("XX", "X.", "X.") to 27,
        Piece.of("XXX", "..X", "..X") to 26,
        Piece.of(".X", ".X", "XX") to 25,
        Piece.of("XXX", "X..", "X..") to 25,
        Piece.of(".X", "XX", "X.") to 20,
        Piece.of("XXX", ".X.") to 18,
        Piece.of("XX", ".X", ".X") to 15,
        Piece.of("XXX", "X..") to 12,
        Piece.of("..X", "XXX") to 12,
        Piece.of("X.", "X.", "XX") to 9,
        // The diagonal came up seven times, six of them this way round.
        Piece.of("..X", ".X.", "X..") to 4,
        Piece.of("X..", ".X.", "..X") to 3,
    )

    val all: List<Piece> = counted.map { it.first }

    /** How likely each shape of [all] is to be handed out; they add up to 1. */
    val shares: DoubleArray = counted.sumOf { it.second }.let { total -> DoubleArray(counted.size) { counted[it].second.toDouble() / total } }

    private val upTo = DoubleArray(shares.size).also { sums ->
        var sum = 0.0
        for (index in shares.indices) {
            sum += shares[index]
            sums[index] = sum
        }
    }

    /** The index in [all] of a shape drawn the way the game was counted to draw. */
    fun randomIndex(random: Random): Int {
        val at = random.nextDouble()
        val index = upTo.indexOfFirst { at < it }
        return if (index < 0) upTo.lastIndex else index
    }

    fun random(random: Random): Piece = all[randomIndex(random)]
}
