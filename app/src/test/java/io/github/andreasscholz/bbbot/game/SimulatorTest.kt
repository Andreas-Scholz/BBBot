package io.github.andreasscholz.bbbot.game

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SimulatorTest {

    /** With fewer rounds tried per decision than on the phone, to keep the tests short. */
    private fun quick(weights: Weights) = Solver(HeuristicEvaluator(weights.copy(roundCandidates = 8, roundSamples = 16)))

    @Test
    fun `solver survives long games against random pieces`() {
        val simulator = Simulator(quick(Weights()))
        val games = 20
        val maxRounds = 300
        val results = List(games) { simulator.play(Random(it), maxRounds) }

        val averageRounds = results.sumOf { it.rounds } / games.toDouble()
        println("rounds per game: ${results.map { it.rounds }} (average $averageRounds of $maxRounds)")
        println("lines per round: ${results.sumOf { it.linesCleared } / results.sumOf { it.rounds }.toDouble()}")

        // Random pieces can end any game early; the default weights reach about 250 on average.
        assertTrue("average of $averageRounds rounds is too low", averageRounds >= 150)
    }

    @Test
    fun `the risky strategy scores more per round than the safe one`() {
        // The strategies only part ways once a combo is long, so the games must be long too.
        fun perRound(strategy: Strategy): Double {
            val simulator = Simulator(quick(strategy.weights))
            val results = List(16) { simulator.play(Random(300 + it), maxRounds = 500) }
            return results.sumOf { it.score } / results.sumOf { it.rounds }.toDouble()
        }

        val safe = perRound(Strategy.SAFE)
        val risky = perRound(Strategy.RISKY)
        println("points per round: $safe safe, $risky risky")

        assertTrue("$risky is not more than $safe", risky > safe)
    }

    @Test
    fun `caring about the combo makes combos much longer`() {
        fun averageCombo(weights: Weights): Double {
            val simulator = Simulator(quick(weights))
            return List(12) { simulator.play(Random(100 + it), maxRounds = 150).longestCombo }.average()
        }

        val careless = averageCombo(Weights(comboBreak = 0.0))
        val careful = averageCombo(Weights())
        println("longest combo on average: $careless without, $careful with the combo terms")

        assertTrue("$careful is not much longer than $careless", careful > careless * 2)
    }
}
