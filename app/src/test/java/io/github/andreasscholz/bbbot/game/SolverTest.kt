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
    fun `a combo is worth more the longer it is, up to a limit`() {
        val evaluator = HeuristicEvaluator(Weights(comboBreak = -10.0, comboGrowth = 0.5, comboCap = 4.0))
        assertEquals(-10.0, evaluator.comboWorth(0), 0.0)
        assertEquals(-20.0, evaluator.comboWorth(2), 0.0)
        assertEquals(-40.0, evaluator.comboWorth(6), 0.0)
        assertEquals(-40.0, evaluator.comboWorth(100), 0.0)
    }

    @Test
    fun `a round needs one clear to keep the combo, and no more than one`() {
        // Completing the bottom row leaves a block sticking out; nothing else is at stake.
        val board = Board.parse(*Array(7) { "........" }, "XXXXXX..")
        val evaluator = HeuristicEvaluator(Weights(comboBreak = -1000.0, lookahead = 0.0))
        val solver = Solver(evaluator)
        val piece = listOf(Piece.of("X", "X", "X"))

        // With the round's clear still owed, the piece has to deliver it. It cannot, so the
        // combo is lost whatever is done, and it is lost equally for every placement.
        assertFalse(solver.solve(board, piece, Combo(length = 9)).clearPossible)
        assertEquals(
            solver.solve(board, piece, Combo(length = 9)).placements,
            solver.solve(board, piece, Combo(length = 9, clearedThisRound = true)).placements,
        )
    }

    @Test
    fun `a board with a line about to be completed promises more for the combo`() {
        // Without the look at the boards after the next piece, only the combo is left to rate.
        val evaluator = HeuristicEvaluator(Weights(lookahead = 0.0, comboGrowth = 0.1, comboCap = 100.0))
        val nearlyDone = Board.parse(*Array(7) { "........" }, "XXXXXXX.")
        val scattered = Board.parse(*Array(7) { "........" }, "X.X.X.X.")

        for (length in listOf(0, 5, 50)) {
            assertTrue(evaluator.outlook(nearlyDone.bits, length).comboRisk > evaluator.outlook(scattered.bits, length).comboRisk)
        }
        // The longer the combo, the more the difference counts.
        fun advantage(length: Int) =
            evaluator.outlook(nearlyDone.bits, length).comboRisk - evaluator.outlook(scattered.bits, length).comboRisk
        assertTrue(advantage(50) > advantage(0))
    }

    @Test
    fun `tells whether the offered pieces could clear a line`() {
        val board = Board.parse(*Array(7) { "........" }, "XXXXX...")

        assertTrue(solver.solve(board, listOf(Piece.of("XXX"))).clearPossible)
        assertTrue(solver.solve(board, listOf(Piece.of("XX"), Piece.of("X"))).clearPossible)
        assertFalse(solver.solve(board, listOf(Piece.of("XX"))).clearPossible)
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
