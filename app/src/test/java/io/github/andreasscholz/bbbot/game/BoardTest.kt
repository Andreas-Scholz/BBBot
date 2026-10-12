package io.github.andreasscholz.bbbot.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardTest {

    @Test
    fun `piece knows its size`() {
        val corner = Piece.of("X..", "X..", "XXX")
        assertEquals(3, corner.width)
        assertEquals(3, corner.height)
        assertEquals(5, corner.cellCount)
        assertEquals(5, Piece.of("XXXXX").width)
        assertEquals(1, Piece.of("XXXXX").height)
        assertEquals(5, Piece.of("X", "X", "X", "X", "X").height)
    }

    @Test
    fun `piece is the same wherever its cells were given`() {
        assertEquals(Piece.of("XX", "X."), Piece.ofCells(listOf(4 to 5, 4 to 6, 5 to 5)))
        assertEquals("XX\nX.", Piece.of("...", ".XX", ".X.").toString())
    }

    @Test
    fun `catalog holds every shape once, with shares that add up`() {
        val catalog = PieceCatalog.all
        assertEquals(catalog.size, catalog.distinct().size)
        assertEquals(catalog.size, PieceCatalog.shares.size)
        assertEquals(1.0, PieceCatalog.shares.sum(), 1e-9)
        assertTrue(Piece.of("XXXXX") in catalog)
        assertTrue(Piece.of("X", "X", "X", "X", "X") in catalog)
    }

    @Test
    fun `pieces are drawn as often as the game hands them out`() {
        val random = kotlin.random.Random(1)
        val draws = 20_000
        val squares = (1..draws).count { PieceCatalog.random(random) == Piece.of("XXX", "XXX", "XXX") }
        // The 3×3 square is nearly one piece in seven, over all boards.
        assertEquals(0.136, squares / draws.toDouble(), 0.01)
    }

    @Test
    fun `piece fits only on free cells inside the board`() {
        val board = Board.EMPTY.place(Piece.of("XX", "XX"), 0, 0).board
        val line = Piece.of("XXX")
        assertFalse(board.fits(line, 0, 1))
        assertTrue(board.fits(line, 0, 2))
        assertTrue(board.fits(line, 2, 0))
        assertFalse(board.fits(line, 0, 6))
        assertFalse(board.fits(line, -1, 0))
    }

    @Test
    fun `full row is cleared`() {
        val board = Board.parse(
            "XXXXX...",
            "X.......",
            "........",
            "........",
            "........",
            "........",
            "........",
            "........",
        )
        val result = board.place(Piece.of("XXX"), 0, 5)
        assertEquals(1, result.linesCleared)
        assertEquals(1, result.board.filledCount)
        assertTrue(result.board[1, 0])
    }

    @Test
    fun `row and column completed by the same piece are both cleared`() {
        val board = Board.parse(
            ".XXXXXXX",
            "X.......",
            "X.......",
            "X.......",
            "X.......",
            "X.......",
            "X.......",
            "X......X",
        )
        val result = board.place(Piece.of("X"), 0, 0)
        assertEquals(2, result.linesCleared)
        assertEquals(Board.parse(*Array(7) { "........" }, ".......X"), result.board)
    }

    @Test
    fun `board without room for a piece says so`() {
        val checkered = Board.parse(*Array(8) { if (it % 2 == 0) "X.X.X.X." else ".X.X.X.X" })
        assertTrue(checkered.fitsAnywhere(Piece.of("X")))
        assertFalse(checkered.fitsAnywhere(Piece.of("XX")))
        assertTrue(checkered.fitsAnywhere(Piece.of("X.", ".X")))
    }
}
