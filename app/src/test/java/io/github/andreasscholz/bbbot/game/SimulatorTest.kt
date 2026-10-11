package io.github.andreasscholz.bbbot.game

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SimulatorTest {

    @Test
    fun `solver survives long games against random pieces`() {
        val simulator = Simulator(Solver())
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
    fun `the strategies trade safety for points in the promised order`() {
        fun play(strategy: Strategy): List<GameResult> {
            val simulator = Simulator(Solver(HeuristicEvaluator(strategy.weights)))
            return List(24) { simulator.play(Random(300 + it), maxRounds = 200) }
        }
        fun perRound(results: List<GameResult>) = results.sumOf { it.score } / results.sumOf { it.rounds }.toDouble()

        val safe = play(Strategy.SAFE)
        val balanced = play(Strategy.BALANCED)
        val risky = play(Strategy.RISKY)
        println("rounds: ${safe.sumOf { it.rounds }} safe, ${balanced.sumOf { it.rounds }} balanced, ${risky.sumOf { it.rounds }} risky")
        println("points per round: ${perRound(safe)} safe, ${perRound(balanced)} balanced, ${perRound(risky)} risky")

        assertTrue(safe.sumOf { it.rounds } > risky.sumOf { it.rounds })
        assertTrue(perRound(safe) < perRound(balanced))
        assertTrue(perRound(balanced) < perRound(risky))
    }

    @Test
    fun `caring about the combo makes combos much longer`() {
        fun averageCombo(weights: Weights): Double {
            val simulator = Simulator(Solver(HeuristicEvaluator(weights)))
            return List(12) { simulator.play(Random(100 + it), maxRounds = 150).longestCombo }.average()
        }

        val careless = averageCombo(Weights(comboBreak = 0.0))
        val careful = averageCombo(Weights())
        println("longest combo on average: $careless without, $careful with the combo terms")

        assertTrue("$careful is not much longer than $careless", careful > careless * 2)
    }
}
