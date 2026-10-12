package io.github.andreasscholz.bbbot.game

import kotlin.random.Random

/**
 * The shapes the game hands out and how often, counted in traces of real games: 6227 pieces in
 * 2117 rounds (see the README). The bot does not depend on this list to recognize pieces; it is
 * used to judge what a board has to expect and to play simulated games.
 *
 * The game does not draw evenly, and it looks at the board: the fuller it is, the more compact
 * rectangles come up, which are easy to place, and the fewer of the awkward shapes. A 3×3 square
 * is one piece in ten on a nearly empty board and one in seven on a full one. Single blocks and
 * the short lines of two and three hardly come up at all.
 */
object PieceCatalog {

    /** Where a board counts as fuller: from this many blocks on, see [counted]. */
    private val FULLER = intArrayOf(0, 10, 15, 20)

    /** Every shape with how often it was dealt onto a board with 0 to 9, 10 to 14, 15 to 19 and 20 or more blocks. */
    private val counted: List<Pair<Piece, IntArray>> = listOf(
        Piece.of("XXX", "XXX", "XXX") to intArrayOf(127, 168, 250, 221),
        Piece.of("XX", "XX") to intArrayOf(74, 118, 144, 166),
        Piece.of("X", "X", "X", "X") to intArrayOf(95, 115, 123, 116),
        Piece.of("XXX", "XXX") to intArrayOf(41, 89, 117, 91),
        Piece.of("XX", "XX", "XX") to intArrayOf(38, 106, 87, 93),
        Piece.of("XXXXX") to intArrayOf(48, 72, 92, 73),
        Piece.of("XXXX") to intArrayOf(45, 70, 80, 67),
        Piece.of("X", "X", "X", "X", "X") to intArrayOf(53, 64, 53, 61),
        Piece.of("X..", "XXX") to intArrayOf(57, 56, 50, 54),
        Piece.of(".XX", "XX.") to intArrayOf(62, 50, 46, 51),
        Piece.of(".X.", "XXX") to intArrayOf(49, 50, 51, 51),
        Piece.of("X.", "XX", ".X") to intArrayOf(41, 56, 45, 44),
        Piece.of("X.", "XX", "X.") to intArrayOf(39, 54, 54, 35),
        Piece.of("XX.", ".XX") to intArrayOf(38, 53, 48, 42),
        Piece.of(".X", "XX", ".X") to intArrayOf(40, 50, 47, 33),
        Piece.of("XXX", "..X") to intArrayOf(38, 61, 34, 35),
        Piece.of(".X", "XX", "X.") to intArrayOf(52, 49, 30, 35),
        Piece.of("XX", "X.", "X.") to intArrayOf(37, 47, 47, 33),
        Piece.of("X..", "X..", "XXX") to intArrayOf(43, 36, 45, 31),
        Piece.of("XXX", "X..", "X..") to intArrayOf(47, 37, 45, 26),
        Piece.of("XXX", "..X", "..X") to intArrayOf(37, 40, 45, 32),
        Piece.of(".X", ".X", "XX") to intArrayOf(31, 43, 47, 28),
        Piece.of("XXX", ".X.") to intArrayOf(48, 39, 33, 27),
        Piece.of("..X", "..X", "XXX") to intArrayOf(39, 35, 36, 29),
        Piece.of("XX", ".X", ".X") to intArrayOf(25, 26, 22, 15),
        Piece.of("..X", "XXX") to intArrayOf(11, 19, 22, 27),
        Piece.of("XXX", "X..") to intArrayOf(4, 29, 23, 17),
        Piece.of("X.", "X.", "XX") to intArrayOf(5, 22, 16, 21),
        // The diagonals are rare, and came up more often this way round.
        Piece.of("..X", ".X.", "X..") to intArrayOf(4, 8, 1, 2),
        Piece.of("X..", ".X.", "..X") to intArrayOf(1, 2, 4, 1),
    )

    val all: List<Piece> = counted.map { it.first }

    /** How likely each shape of [all] is to be handed out, whatever the board; they add up to 1. */
    val shares: DoubleArray = shares { it.sum() }

    /** The same for a board with as many blocks as each entry of [FULLER] begins with. */
    private val sharesByFill: List<DoubleArray> = FULLER.indices.map { level -> shares { it[level] } }

    private fun shares(count: (IntArray) -> Int): DoubleArray {
        val total = counted.sumOf { count(it.second) }
        return DoubleArray(counted.size) { count(counted[it].second).toDouble() / total }
    }

    /** How likely each shape of [all] is to be handed out onto a board with [filled] blocks. */
    fun sharesAt(filled: Int): DoubleArray = sharesByFill[FULLER.indexOfLast { filled >= it }]

    /** The index in [all] of a shape drawn the way the game was counted to draw for a board with [filled] blocks. */
    fun randomIndex(random: Random, filled: Int): Int {
        val shares = sharesAt(filled)
        var at = random.nextDouble()
        for (index in shares.indices) {
            at -= shares[index]
            if (at < 0) return index
        }
        return shares.lastIndex
    }

    fun random(random: Random, filled: Int): Piece = all[randomIndex(random, filled)]

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
