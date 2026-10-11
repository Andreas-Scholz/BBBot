package io.github.andreasscholz.bbbot.game

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SimulatorTest {

    private fun quick(weights: Weights) = Solver(HeuristicEvaluator(weights))

    @Test
    fun `solver survives long games against random pieces`() {
        val simulator = Simulator(quick(Weights()))
        val games = 20
        val maxRounds = 300
        val results = List(games) { simulator.play(Random(it), maxRounds) }

        val averageRounds = results.sumOf { it.rounds } / games.toDouble()
        println("rounds per game: ${results.map { it.rounds }} (average $averageRounds of $maxRounds)")
        println("lines per round: ${results.sumOf { it.linesCleared } / results.sumOf { it.rounds }.toDouble()}")

        // A bad round can end any game early; the default weights reach about 200 on average.
        assertTrue("average of $averageRounds rounds is too low", averageRounds >= 120)
    }

    @Test
    fun `every strategy scores far more per round than a solver that does not care about combos`() {
        fun perRound(weights: Weights): Double {
            val simulator = Simulator(quick(weights))
            val results = List(12) { simulator.play(Random(300 + it), maxRounds = 300) }
            return results.sumOf { it.score } / results.sumOf { it.rounds }.toDouble()
        }

        val careless = perRound(Weights(comboBreak = 0.0))
        for (strategy in Strategy.entries) {
            val points = perRound(strategy.weights)
            println("points per round: $points ${strategy.name.lowercase()}, $careless without care for the combo")
            assertTrue("$points is not much more than $careless", points > careless * 2)
        }
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
