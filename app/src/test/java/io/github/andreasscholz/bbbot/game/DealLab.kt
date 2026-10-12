package io.github.andreasscholz.bbbot.game

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * A tool rather than a test: compares the rounds the game dealt, as the traces recorded them,
 * with rounds drawn at random for the same boards, by the share each shape has over all boards.
 * It answers whether the game picks its pieces with an eye on the board, and how.
 *
 * ```sh
 * BBBOT_DEALS=captures ./gradlew :app:testDebugUnitTest --tests '*DealLab*' -i | grep DEAL
 * ```
 *
 * `captures` is a folder with the `trace-*` folders of the phone. With `BBBOT_DEALS_OUT` set to
 * a file, a line per round is written there as well.
 */
class DealLab {

    private class Round(val trace: String, val board: Board, val pieces: List<Piece>, val combo: Int, val cleared: Boolean)

    @Test
    fun `compare the rounds dealt with rounds drawn at random`() {
        val folder = System.getenv("BBBOT_DEALS")
        assumeTrue("BBBOT_DEALS is not set", folder != null)
        val rounds = File(folder!!).listFiles { file -> file.name.startsWith("trace-") }.orEmpty().sorted().flatMap(::read)
        val random = Random(1)
        val solver = Solver(HeuristicEvaluator(Strategy.BALANCED.weights))
        val out = System.getenv("BBBOT_DEALS_OUT")?.let { File(it).printWriter() }
        out?.println(
            "trace,filled,combo,cleared,cells,fits,clear_possible,clearing_pieces,lines_planned," +
                "random_cells,random_fits,random_clear_possible,random_clearing_pieces,random_piece_fits,random_lines_planned",
        )
        for (round in rounds) {
            val samples = List(SAMPLES) { List(round.pieces.size) { PieceCatalog.random(random) } }
            val fitting = samples.filter { RoundSampler.fits(round.board, it) }
            out?.println(
                listOf(
                    round.trace,
                    round.board.filledCount,
                    round.combo,
                    round.cleared,
                    round.pieces.sumOf { it.cellCount },
                    RoundSampler.fits(round.board, round.pieces),
                    canClear(round.board.bits, round.pieces, used = 0),
                    round.pieces.count { clearsAtOnce(round.board, it) },
                    solver.solve(round.board, round.pieces, Combo(round.combo)).linesCleared,
                    // What a piece drawn by its share alone would do, worked out rather than sampled.
                    PieceCatalog.all.indices.sumOf { PieceCatalog.shares[it] * PieceCatalog.all[it].cellCount } * round.pieces.size,
                    fitting.size / SAMPLES.toDouble(),
                    // Among the rounds that fit, as the game deals no others.
                    if (fitting.isEmpty()) "" else fitting.count { canClear(round.board.bits, it, used = 0) } / fitting.size.toDouble(),
                    PieceCatalog.all.indices.sumOf { if (clearsAtOnce(round.board, PieceCatalog.all[it])) PieceCatalog.shares[it] else 0.0 } *
                        round.pieces.size,
                    PieceCatalog.all.indices.sumOf { if (round.board.fitsAnywhere(PieceCatalog.all[it])) PieceCatalog.shares[it] else 0.0 },
                    // What the solver makes of drawn rounds on the same board, with the same combo.
                    fitting.take(PLANNED).map { solver.solve(round.board, it, Combo(round.combo)).linesCleared }.let { if (it.isEmpty()) "" else it.average() },
                ).joinToString(","),
            )
        }
        out?.close()
        println("DEAL ${rounds.size} rounds read")
    }

    /** The rounds of a trace that were seen from their first piece on. */
    private fun read(folder: File): List<Round> {
        val file = File(folder, "moves.csv")
        if (!file.exists()) return emptyList()
        val lines = file.readLines()
        val header = lines.first().split(",")
        if ("offered" !in header || "combo_length" !in header) return emptyList()
        val rows = lines.drop(1).map { line -> header.zip(line.split(",")).toMap() }
        return rows.groupBy { it.getValue("round") }.values.mapNotNull { round ->
            val first = round.first()
            val offered = first.getValue("offered").split(";").filter { it.isNotEmpty() }
            if (offered.size != 3 || round.size != 3) return@mapNotNull null
            Round(
                trace = folder.name,
                board = Board(java.lang.Long.parseUnsignedLong(first.getValue("board"), 16)),
                pieces = offered.map { piece(java.lang.Long.parseUnsignedLong(it.substringAfter(':'), 16)) },
                combo = first.getValue("combo_length").toInt(),
                cleared = round.any { it.getValue("lines").toInt() > 0 },
            )
        }
    }

    private fun piece(mask: Long): Piece =
        Piece.ofCells((0 until 64).filter { mask ushr it and 1L != 0L }.map { it / Board.SIZE to it % Board.SIZE })

    /** Whether [piece] can complete a line of [board] as it is. */
    private fun clearsAtOnce(board: Board, piece: Piece): Boolean {
        for (row in 0..Board.SIZE - piece.height) {
            for (col in 0..Board.SIZE - piece.width) {
                if (board.fits(piece, row, col) && Board.fullLines(board.bits or piece.maskAt(row, col)) != 0L) return true
            }
        }
        return false
    }

    /** Whether the [pieces] not yet [used] can complete a line in any order and position. */
    private fun canClear(bits: Long, pieces: List<Piece>, used: Int): Boolean {
        for (index in pieces.indices) {
            if (used and (1 shl index) != 0) continue
            val piece = pieces[index]
            for (row in 0..Board.SIZE - piece.height) {
                for (col in 0..Board.SIZE - piece.width) {
                    val cells = piece.maskAt(row, col)
                    if (bits and cells != 0L) continue
                    if (Board.fullLines(bits or cells) != 0L) return true
                    if (canClear(bits or cells, pieces, used or (1 shl index))) return true
                }
            }
        }
        return false
    }

    private companion object {
        const val SAMPLES = 40

        /** For how many of them the solver is asked what it would do. */
        const val PLANNED = 8
    }
}
