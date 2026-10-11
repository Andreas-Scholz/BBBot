package io.github.andreasscholz.bbbot.game

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.stream.IntStream
import kotlin.random.Random

/**
 * A tool rather than a test: lets sets of weights play many simulated games and prints how they
 * did. This is where the strategies of [Strategy] come from.
 *
 * ```sh
 * BBBOT_LAB=1 ./gradlew :app:testDebugUnitTest --tests '*StrategyLab*' -i | grep LAB
 * ```
 *
 * The games are played against uniformly drawn pieces and scored by the simulator's imitation
 * of the game, so the numbers rank the sets against each other and say little about the points
 * to expect on the phone.
 */
class StrategyLab {

    private val games = (System.getenv("BBBOT_LAB_GAMES") ?: "192").toInt()
    private val maxRounds = (System.getenv("BBBOT_LAB_ROUNDS") ?: "1000").toInt()

    @Test
    fun `compare sets of weights`() {
        assumeTrue("BBBOT_LAB is not set", System.getenv("BBBOT_LAB") != null)

        println(
            "LAB %-22s %7s %7s %7s %6s %10s %10s %9s %7s"
                .format("", "rounds", "worst10", "median", "alive", "score", "best10", "per round", "combo"),
        )
        for ((name, weights) in candidates()) run(name, weights)
    }

    private fun candidates(): Map<String, Weights> = linkedMapOf(
        "safe" to Strategy.SAFE.weights,
        "balanced" to Strategy.BALANCED.weights,
        "risky" to Strategy.RISKY.weights,
    )

    private fun run(name: String, weights: Weights) {
        val results = IntStream.range(0, games).parallel()
            .mapToObj { Simulator(Solver(HeuristicEvaluator(weights))).play(Random(5000 + it), maxRounds) }
            .toList()
        val rounds = results.map { it.rounds }.sorted()
        val scores = results.map { it.score }.sorted()
        // worst10 and best10: one game in ten ends earlier, one game in ten scores higher.
        println(
            "LAB %-22s %7.1f %7d %7d %5.0f%% %10.0f %10d %9.1f %7.1f".format(
                name,
                rounds.average(),
                rounds[rounds.size / 10],
                rounds[rounds.size / 2],
                100.0 * rounds.count { it == maxRounds } / games,
                scores.average(),
                scores[scores.size * 9 / 10],
                results.sumOf { it.score } / rounds.sum().toDouble(),
                results.map { it.longestCombo }.average(),
            ),
        )
    }
}
