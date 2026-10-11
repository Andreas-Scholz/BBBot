package io.github.andreasscholz.bbbot.bot

import io.github.andreasscholz.bbbot.game.Piece
import io.github.andreasscholz.bbbot.game.Placement
import io.github.andreasscholz.bbbot.vision.BoardGeometry
import io.github.andreasscholz.bbbot.vision.TrayPiece
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DragPlanTest {

    private val geometry = BoardGeometry(left = 61f, top = 580f, cellSize = 119.7f)
    private val square = Piece.of("XX", "XX")

    @Test
    fun `finger path matches a drag that worked in the game`() {
        // Lifting the finger at 594, 1492 put this square on rows 3 and 4, columns 5 and 6.
        val plan = DragPlan.of(geometry, TrayPiece(0, square, 225f, 1818f), Placement(0, square, row = 3, col = 5))

        assertEquals(225f, plan.fromX, 0f)
        assertEquals(1818f, plan.fromY, 0f)
        assertEquals(594f, plan.toX, 2f)
        assertEquals(1492f, plan.toY, 2f)
    }

    @Test
    fun `finger stays on the screen for every place on the board`() {
        val line = Piece.of("XXXXX")
        val column = Piece.of("X", "X", "X", "X", "X")
        for (slotX in listOf(225f, 540f, 855f)) {
            for (piece in listOf(Piece.of("X"), line, column)) {
                for (row in listOf(0, 8 - piece.height)) {
                    for (col in listOf(0, 8 - piece.width)) {
                        val plan = DragPlan.of(geometry, TrayPiece(0, piece, slotX, 1818f), Placement(0, piece, row, col))
                        assertTrue("$plan leaves the screen", plan.toX in 0f..1080f && plan.toY in 0f..2400f)
                    }
                }
            }
        }
    }
}
