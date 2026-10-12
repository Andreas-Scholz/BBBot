package io.github.andreasscholz.bbbot.game

import kotlin.random.Random

/**
 * The shapes the game hands out and how often, counted in traces of real games: 12743 pieces in
 * 4311 rounds (see the README). The bot does not depend on this list to recognize pieces; it is
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
        Piece.of("XXX", "XXX", "XXX") to intArrayOf(146, 239, 472, 923),
        Piece.of("XX", "XX") to intArrayOf(90, 171, 288, 566),
        Piece.of("X", "X", "X", "X") to intArrayOf(115, 155, 229, 366),
        Piece.of("XXX", "XXX") to intArrayOf(49, 132, 221, 363),
        Piece.of("XX", "XX", "XX") to intArrayOf(47, 149, 185, 346),
        Piece.of("XXXXX") to intArrayOf(52, 84, 146, 293),
        Piece.of("XXXX") to intArrayOf(49, 90, 147, 222),
        Piece.of("X", "X", "X", "X", "X") to intArrayOf(67, 85, 119, 218),
        Piece.of("X..", "XXX") to intArrayOf(61, 83, 102, 187),
        Piece.of(".XX", "XX.") to intArrayOf(69, 69, 95, 163),
        Piece.of(".X.", "XXX") to intArrayOf(62, 68, 104, 150),
        Piece.of("X.", "XX", ".X") to intArrayOf(51, 74, 89, 143),
        Piece.of("X.", "XX", "X.") to intArrayOf(48, 70, 98, 138),
        Piece.of("XX.", ".XX") to intArrayOf(50, 67, 93, 128),
        Piece.of(".X", "XX", ".X") to intArrayOf(45, 73, 100, 125),
        Piece.of("XXX", "..X") to intArrayOf(41, 81, 82, 112),
        Piece.of(".X", "XX", "X.") to intArrayOf(58, 71, 71, 131),
        Piece.of("XX", "X.", "X.") to intArrayOf(43, 61, 80, 103),
        Piece.of("X..", "X..", "XXX") to intArrayOf(55, 50, 71, 102),
        Piece.of("XXX", "X..", "X..") to intArrayOf(56, 51, 78, 97),
        Piece.of("XXX", "..X", "..X") to intArrayOf(41, 52, 84, 116),
        Piece.of(".X", ".X", "XX") to intArrayOf(38, 65, 91, 116),
        Piece.of("XXX", ".X.") to intArrayOf(60, 57, 59, 120),
        Piece.of("..X", "..X", "XXX") to intArrayOf(48, 52, 63, 94),
        Piece.of("XX", ".X", ".X") to intArrayOf(25, 37, 46, 65),
        Piece.of("..X", "XXX") to intArrayOf(12, 21, 38, 69),
        Piece.of("XXX", "X..") to intArrayOf(5, 37, 46, 67),
        Piece.of("X.", "X.", "XX") to intArrayOf(8, 32, 37, 62),
        // The diagonals are rare, and came up more often this way round.
        Piece.of("..X", ".X.", "X..") to intArrayOf(4, 12, 12, 12),
        Piece.of("X..", ".X.", "..X") to intArrayOf(1, 4, 5, 7),
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
