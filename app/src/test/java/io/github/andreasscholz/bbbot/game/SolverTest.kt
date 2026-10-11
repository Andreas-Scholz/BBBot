package io.github.andreasscholz.bbbot.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SolverTest {

    private val solver = Solver()

    @Test
    fun `completes a row when it can`() {
        val board = Board.parse(
            "........",
            "........",
            "........",
            "........",
            "........",
            "........",
            "........",
            "XXXXX...",
        )
        val plan = solver.solve(board, listOf(Piece.of("XXX")))
        assertEquals(listOf(Placement(0, Piece.of("XXX"), 7, 5)), plan.placements)
        assertEquals(1, plan.linesCleared)
        assertEquals(Board.EMPTY, plan.board)
        assertTrue(plan.complete)
    }

    @Test
    fun `finds the order in which all pieces fit`() {
        // The square has no room until the line completes the top row and clears it.
        val board = Board.parse(
            ".....XXX",
            "XXXXX...",
            "XXXXX...",
            "X.X.X.X.",
            ".X.X.X.X",
            "X.X.X.X.",
            ".X.X.X.X",
            "X.X.X.X.",
        )
        val line = Piece.of("XXXXX")
        val square = Piece.of("XXX", "XXX", "XXX")
        assertFalse(board.fitsAnywhere(square))

        val plan = solver.solve(board, listOf(square, line))

        assertTrue(plan.complete)
        assertEquals(listOf(Placement(1, line, 0, 0), Placement(0, square, 0, 5)), plan.placements)
        assertEquals(3, plan.linesCleared)
    }

    @Test
    fun `places as many pieces as possible when the game is lost`() {
        val board = Board.parse(
            "X.X.X.X.",
            ".X.X.X.X",
            "X.X.X.X.",
            ".X.X.X.X",
            "X.X.X.X.",
            ".X.X.X.X",
            "X.X.X.X.",
            ".X.X.X..",
        )
        val plan = solver.solve(board, listOf(Piece.of("XX", "XX"), Piece.of("X"), Piece.of("XXX")))
        assertFalse(plan.complete)
        assertEquals(listOf(1), plan.placements.map { it.pieceIndex })
    }

    @Test
    fun `reports nothing to place on a full board`() {
        val full = Board.parse(*Array(8) { if (it % 2 == 0) "X.X.X.X." else ".X.X.X.X" })
        val plan = solver.solve(full, listOf(Piece.of("XX")))
        assertTrue(plan.placements.isEmpty())
        assertFalse(plan.complete)
        assertEquals(full, plan.board)
    }

    @Test
    fun `identical pieces are placed once each`() {
        val square = Piece.of("XX", "XX")
        val plan = solver.solve(Board.EMPTY, listOf(square, square, square))
        assertTrue(plan.complete)
        assertEquals(setOf(0, 1, 2), plan.placements.map { it.pieceIndex }.toSet())
        assertEquals(12, plan.board.filledCount)
    }
}
