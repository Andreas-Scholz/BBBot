package io.github.andreasscholz.bbbot.game

import kotlin.random.Random

/** What a board has to expect from the next round: the chances that it can clear a line and that its pieces fit. */
class RoundChances(val clear: Double, val fit: Double)

/**
 * Tries a board against rounds of three pieces drawn at random, to count what the solver
 * otherwise has to guess: how often the next round could clear a line, which keeps the combo,
 * and how often its pieces would not all fit, which ends the game.
 *
 * The same rounds are used for every board of one decision, so that boards are compared on
 * equal terms and not by the luck of the draw.
 */
internal class RoundSampler(samples: Int, seed: Long) {

    private val shapes = PieceCatalog.all
    private val masks = LongArray(shapes.size) { shapes[it].mask }
    private val widths = IntArray(shapes.size) { shapes[it].width }
    private val heights = IntArray(shapes.size) { shapes[it].height }

    /** The rounds, three indices into the shapes each. */
    private val rounds = Random(seed).let { random -> IntArray(samples * ROUND) { random.nextInt(shapes.size) } }
    private val round = IntArray(ROUND)
    private var budget = 0

    fun chances(bits: Long): RoundChances {
        var clear = 0
        var fit = 0
        val count = rounds.size / ROUND
        for (sample in 0 until count) {
            for (i in 0 until ROUND) round[i] = rounds[sample * ROUND + i]
            // A round that clears something is one whose pieces can be placed, at least up to there.
            if (canClear(bits)) clear++
            budget = FIT_BUDGET
            if (fits(bits, remaining = (1 shl ROUND) - 1)) fit++
        }
        return RoundChances(clear.toDouble() / count, fit.toDouble() / count)
    }

    /**
     * Whether one piece of the round can clear a line, at once or after another piece was placed.
     * Clears that need all three pieces are left out: they are rare and looking for them is slow.
     */
    private fun canClear(bits: Long): Boolean {
        for (first in 0 until ROUND) {
            if (clearsAnywhere(bits, round[first])) return true
        }
        for (first in 0 until ROUND) {
            val shape = round[first]
            for (row in 0..Board.SIZE - heights[shape]) {
                for (col in 0..Board.SIZE - widths[shape]) {
                    val cells = masks[shape] shl (row * Board.SIZE + col)
                    if (bits and cells != 0L) continue
                    for (second in 0 until ROUND) {
                        if (second != first && clearsAnywhere(bits or cells, round[second])) return true
                    }
                }
            }
        }
        return false
    }

    private fun clearsAnywhere(bits: Long, shape: Int): Boolean {
        for (row in 0..Board.SIZE - heights[shape]) {
            for (col in 0..Board.SIZE - widths[shape]) {
                val cells = masks[shape] shl (row * Board.SIZE + col)
                if (bits and cells == 0L && Board.fullLines(bits or cells) != 0L) return true
            }
        }
        return false
    }

    /** Whether the pieces of the round can all be placed, in some order, clearing lines on the way. */
    private fun fits(bits: Long, remaining: Int): Boolean {
        if (remaining == 0) return true
        for (index in 0 until ROUND) {
            if (remaining and (1 shl index) == 0) continue
            val shape = round[index]
            for (row in 0..Board.SIZE - heights[shape]) {
                for (col in 0..Board.SIZE - widths[shape]) {
                    val cells = masks[shape] shl (row * Board.SIZE + col)
                    if (bits and cells != 0L) continue
                    // On a crowded board the search for a way to fit could go on for long; a board
                    // that needs that much looking is as good as lost.
                    if (--budget < 0) return false
                    val filled = bits or cells
                    if (fits(filled and Board.fullLines(filled).inv(), remaining and (1 shl index).inv())) return true
                }
            }
        }
        return false
    }

    private companion object {
        const val ROUND = HeuristicEvaluator.ROUND
        const val FIT_BUDGET = 20_000
    }
}
