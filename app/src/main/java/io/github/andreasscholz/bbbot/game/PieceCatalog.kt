package io.github.andreasscholz.bbbot.game

/**
 * The shapes Block Blast hands out, in every orientation. The bot does not depend on this list
 * to recognize pieces; it is used to judge how many shapes still fit on a board and to play
 * simulated games.
 */
object PieceCatalog {

    private val baseShapes = listOf(
        Piece.of("X"),
        Piece.of("XX"),
        Piece.of("XXX"),
        Piece.of("XXXX"),
        Piece.of("XXXXX"),
        Piece.of("XX", "XX"),
        Piece.of("XXX", "XXX"),
        Piece.of("XXX", "XXX", "XXX"),
        // Corners
        Piece.of("X.", "XX"),
        Piece.of("X..", "X..", "XXX"),
        // L, T and S tetrominoes
        Piece.of("X.", "X.", "XX"),
        Piece.of("XXX", ".X."),
        Piece.of(".XX", "XX."),
        // Diagonals
        Piece.of("X.", ".X"),
        Piece.of("X..", ".X.", "..X"),
    )

    val all: List<Piece> = baseShapes.flatMap { orientationsOf(it) }.distinct()

    private fun orientationsOf(piece: Piece): List<Piece> {
        val result = mutableListOf<Piece>()
        var cells = (0 until piece.height).flatMap { row ->
            (0 until piece.width).filter { col -> piece[row, col] }.map { col -> row to col }
        }
        repeat(4) {
            result += Piece.ofCells(cells)
            result += Piece.ofCells(cells.map { (row, col) -> row to -col })
            cells = cells.map { (row, col) -> col to -row }
        }
        return result.distinct()
    }
}
