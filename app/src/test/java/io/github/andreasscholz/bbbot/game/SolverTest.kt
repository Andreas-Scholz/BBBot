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
    fun `losing the combo costs, and only at the third piece without a clear`() {
        val evaluator = HeuristicEvaluator()
        assertEquals(0.0, evaluator.comboReward(gap = 1), 0.0)
        assertEquals(0.0, evaluator.comboReward(gap = 2), 0.0)
        assertTrue(evaluator.comboReward(gap = 3) < 0)
    }

    @Test
    fun `a board with a line about to be completed promises more for the combo`() {
        // Without the look at the boards after the next piece, only the combo is left to rate.
        val evaluator = HeuristicEvaluator(Weights(lookahead = 0.0))
        val nearlyDone = Board.parse(*Array(7) { "........" }, "XXXXXXX.")
        val scattered = Board.parse(*Array(7) { "........" }, "X.X.X.X.")

        for (gap in 0..2) {
            assertTrue(evaluator.outlook(nearlyDone.bits, gap) > evaluator.outlook(scattered.bits, gap))
        }
        // The fewer pieces are left to save the combo, the more the difference counts.
        fun advantage(gap: Int) = evaluator.outlook(nearlyDone.bits, gap) - evaluator.outlook(scattered.bits, gap)
        assertTrue(advantage(2) > advantage(0))
    }

    @Test
    fun `tells whether the offered pieces could clear a line`() {
        val board = Board.parse(*Array(7) { "........" }, "XXXXX...")

        assertTrue(solver.solve(board, listOf(Piece.of("XXX"))).clearPossible)
        assertTrue(solver.solve(board, listOf(Piece.of("XX"), Piece.of("X"))).clearPossible)
        assertFalse(solver.solve(board, listOf(Piece.of("XX"))).clearPossible)
    }

    @Test
    fun `pieces that could not clear anything do not use up the combo`() {
        // One block short of a full row, and a piece that cannot fill the gap.
        val board = Board.parse(*Array(7) { "........" }, "XXXXXXX.")
        val square = listOf(Piece.of("XX", "XX"))

        // Had the combo counted as lost, there would be nothing to choose by; it counts as
        // alive, so the plan is the same as with a piece to spare.
        assertEquals(solver.solve(board, square, piecesSinceClear = 0), solver.solve(board, square, piecesSinceClear = 2))
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
