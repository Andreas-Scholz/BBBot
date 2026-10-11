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

        // Random pieces can end any game early; the default weights reach about 200 on average.
        assertTrue("average of $averageRounds rounds is too low", averageRounds >= 120)
    }
}
