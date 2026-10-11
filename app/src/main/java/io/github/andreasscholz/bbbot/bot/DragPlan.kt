package io.github.andreasscholz.bbbot.bot

import io.github.andreasscholz.bbbot.game.Placement
import io.github.andreasscholz.bbbot.vision.BoardGeometry
import io.github.andreasscholz.bbbot.vision.TrayPiece

/** Where to put the finger down and where to lift it to carry a piece to its place. */
data class DragPlan(val fromX: Float, val fromY: Float, val toX: Float, val toY: Float) {

    companion object {
        /**
         * How far above its place in the tray a piece jumps when it is touched, in board cells.
         * Measured in the game: 269 pixels with cells of 119.7 pixels.
         */
        const val LIFT_CELLS = 2.25f

        /** The game moves a piece one and a half times as far as the finger moves. */
        const val GAIN = 1.5f

        /**
         * The game does not keep a dragged piece under the finger: it lifts the piece by a fixed
         * distance and then moves it faster than the finger. Where the piece is grabbed makes no
         * difference, only how far the finger travels. This works backwards from the place the
         * piece should reach to the place the finger has to stop.
         */
        fun of(geometry: BoardGeometry, trayPiece: TrayPiece, placement: Placement): DragPlan {
            val piece = placement.piece
            val targetX = geometry.x(placement.col + piece.width / 2f)
            val targetY = geometry.y(placement.row + piece.height / 2f)
            val liftedY = trayPiece.centerY - LIFT_CELLS * geometry.cellSize
            return DragPlan(
                fromX = trayPiece.centerX,
                fromY = trayPiece.centerY,
                toX = trayPiece.centerX + (targetX - trayPiece.centerX) / GAIN,
                toY = trayPiece.centerY + (targetY - liftedY) / GAIN,
            )
        }
    }
}
