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
 * With `BBBOT_LAB_OUT` set to a folder, it also writes two files per set there: `<name>.csv`
 * with how the games stood after every round, and `<name>-games.csv` with how each one ended.
 *
 * The games are played against the simulator's imitation of the game, which was fitted to
 * traces but is not the game, so the numbers rank the sets against each other rather than
 * tell the points to expect on the phone.
 */
class StrategyLab {

    private val games = (System.getenv("BBBOT_LAB_GAMES") ?: "192").toInt()
    private val maxRounds = (System.getenv("BBBOT_LAB_ROUNDS") ?: "1000").toInt()
    private val out = System.getenv("BBBOT_LAB_OUT")?.let { java.io.File(it) }

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
        out?.let { write(it, name.replace(' ', '-'), results) }
    }

    /** A game that has ended keeps the score it ended with, so `mean` is what to expect from a game cut off there. */
    private fun write(folder: java.io.File, name: String, results: List<GameResult>) {
        folder.mkdirs()
        java.io.File(folder, "$name-games.csv").printWriter().use { file ->
            file.println("rounds,score,longest_combo,lines,rounds_without_clear,rounds_without_chance,filled")
            for (result in results) {
                file.println(
                    listOf(
                        result.rounds, result.score, result.longestCombo, result.linesCleared,
                        result.roundsWithoutClear, result.roundsWithoutChance,
                        "%.2f".format(java.util.Locale.ROOT, result.filledByRound.average()),
                    ).joinToString(","),
                )
            }
        }
        java.io.File(folder, "$name-combo.csv").printWriter().use { file ->
            file.println("combo_from,rounds,filled,without_clear")
            val from = listOf(0, 1, 5, 20, 60, 120, 200, 400)
            for (index in from.indices) {
                var rounds = 0
                var filled = 0L
                var withoutClear = 0
                for (result in results) {
                    for (round in result.comboByRound.indices) {
                        val combo = if (round == 0) 0 else result.comboByRound[round - 1]
                        if (combo < from[index] || index < from.lastIndex && combo >= from[index + 1]) continue
                        rounds++
                        filled += result.filledByRound[round]
                        if (result.comboByRound[round] == 0) withoutClear++
                    }
                }
                file.println("${from[index]},$rounds,${"%.2f".format(java.util.Locale.ROOT, filled / maxOf(rounds, 1).toDouble())},$withoutClear")
            }
        }
        java.io.File(folder, "$name.csv").printWriter().use { file ->
            file.println("round,alive,mean,p10,median,p90,mean_alive,combo_alive")
            for (round in 1..maxRounds) {
                val alive = results.filter { it.scoreByRound.size >= round }
                val scores = results.map { it.scoreByRound.getOrNull(round - 1) ?: it.score }.sorted()
                file.println(
                    listOf(
                        round,
                        alive.size,
                        scores.average().toLong(),
                        scores[scores.size / 10],
                        scores[scores.size / 2],
                        scores[scores.size * 9 / 10],
                        if (alive.isEmpty()) "" else alive.map { it.scoreByRound[round - 1] }.average().toLong(),
                        if (alive.isEmpty()) "" else "%.1f".format(java.util.Locale.ROOT, alive.map { it.comboByRound[round - 1] }.average()),
                    ).joinToString(","),
                )
            }
        }
    }
}
