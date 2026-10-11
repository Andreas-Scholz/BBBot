package io.github.andreasscholz.bbbot.vision

import io.github.andreasscholz.bbbot.game.Board
import io.github.andreasscholz.bbbot.game.Piece
import kotlin.math.abs
import kotlin.math.roundToInt

/** Where the 8×8 grid is on the screen, in pixels. */
data class BoardGeometry(val left: Float, val top: Float, val cellSize: Float) {
    val size: Float get() = cellSize * Board.SIZE

    /** The screen position [col] cells to the right of the grid's left edge; fractions are allowed. */
    fun x(col: Float): Float = left + col * cellSize

    /** The screen position [row] cells below the grid's top edge. */
    fun y(row: Float): Float = top + row * cellSize
}

/** A piece waiting below the board, with the middle of its bounding box on the screen. */
data class TrayPiece(val slot: Int, val piece: Piece, val centerX: Float, val centerY: Float)

/** Everything the bot needs to know about one screenshot of the game. */
data class GameScreen(val geometry: BoardGeometry, val board: Board, val tray: List<TrayPiece>)

/**
 * Finds the board and the offered pieces in a screenshot.
 *
 * The game changes its theme every now and then, so nothing here knows any colors. The reader
 * only assumes that the background reaches the left edge of the screen, that the board is a
 * large square of something else, that empty cells are darker than blocks, and that the offered
 * pieces lie below the board, one in each third of the screen. It was developed against the
 * wooden and the marble theme.
 */
class ScreenReader {

    /** Returns null when the screenshot does not show a game waiting for a move. */
    fun read(shot: Screenshot): GameScreen? {
        val scene = Scene(shot)
        val frame = findFrame(scene) ?: return null
        val inset = frame.width * FRAME_THICKNESS
        val geometry = BoardGeometry(
            left = frame.left + inset,
            top = frame.top + inset,
            cellSize = (frame.width - 2 * inset) / Board.SIZE,
        )
        val board = readBoard(shot, geometry) ?: return null
        val tray = readTray(scene, frame, geometry) ?: return null
        return GameScreen(geometry, board, tray)
    }

    /**
     * A screenshot together with what counts as background in it. The background of a row is
     * the color at its left edge. How far a pixel may be from that color and still be background
     * depends on the theme: plain wood needs little room, veined marble a lot. The room is
     * measured on the left edge itself, by comparing pixels a short way apart.
     */
    private class Scene(val shot: Screenshot) {
        private val tolerance: Int

        init {
            val distances = IntArray((shot.height - TEXTURE_REACH) / TEXTURE_STEP) {
                val y = it * TEXTURE_STEP
                distance(shot[EDGE_X - 2, y], shot[EDGE_X + 2, y + TEXTURE_REACH])
            }
            distances.sort()
            val typical = distances[distances.size * 99 / 100]
            tolerance = (typical * TEXTURE_FACTOR).toInt().coerceIn(MIN_TOLERANCE, MAX_TOLERANCE)
        }

        fun isForeground(x: Int, y: Int): Boolean = distance(shot[x, y], shot[EDGE_X, y]) > tolerance
    }

    private class Frame(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left + 1
        val height: Int get() = bottom - top + 1
    }

    /** The board with its border: the tallest band that is not background nearly all the way across. */
    private fun findFrame(scene: Scene): Frame? {
        val shot = scene.shot
        val from = (shot.width * 0.1f).toInt()
        val to = (shot.width * 0.9f).toInt()
        val needed = ((to - from) / SCAN_STEP * COVERAGE).toInt()

        var bestTop = 0
        var bestBottom = -1
        var runTop = -1
        var lastHit = -1
        for (y in 0 until shot.height) {
            var hits = 0
            var x = from
            while (x < to) {
                if (scene.isForeground(x, y)) hits++
                x += SCAN_STEP
            }
            if (hits >= needed) {
                // The shaded edges of blocks look like the background, so short gaps are bridged.
                if (runTop < 0 || y - lastHit > shot.width * MAX_GAP) runTop = y
                lastHit = y
                if (lastHit - runTop > bestBottom - bestTop) {
                    bestTop = runTop
                    bestBottom = lastHit
                }
            }
        }
        val height = bestBottom - bestTop + 1
        if (height < shot.width * MIN_BOARD_WIDTH) return null

        val rows = height / SCAN_STEP
        var left = -1
        var right = -1
        for (x in 0 until shot.width) {
            var hits = 0
            var y = bestTop
            while (y <= bestBottom) {
                if (scene.isForeground(x, y)) hits++
                y += SCAN_STEP
            }
            if (hits >= rows * COVERAGE) {
                if (left < 0) left = x
                right = x
            }
        }
        val frame = Frame(left, bestTop, right, bestBottom)
        if (left < 0 || abs(frame.width - frame.height) > frame.width * SQUARE_TOLERANCE) return null
        return frame
    }

    private fun readBoard(shot: Screenshot, geometry: BoardGeometry): Board? {
        val colors = IntArray(Board.SIZE * Board.SIZE)
        for (row in 0 until Board.SIZE) {
            for (col in 0 until Board.SIZE) {
                var red = 0
                var green = 0
                var blue = 0
                for (dy in -1..1) {
                    for (dx in -1..1) {
                        val x = geometry.x(col + 0.5f + dx * SAMPLE_SPREAD).toInt()
                        val y = geometry.y(row + 0.5f + dy * SAMPLE_SPREAD).toInt()
                        val pixel = shot[x, y]
                        red += pixel shr 16 and 0xFF
                        green += pixel shr 8 and 0xFF
                        blue += pixel and 0xFF
                    }
                }
                colors[row * Board.SIZE + col] = (red / 9 shl 16) or (green / 9 shl 8) or (blue / 9)
            }
        }
        // A full board cannot exist, and blocks are brighter than the well they sit in, so the
        // darkest cell is an empty one. Whatever looks different from it is a block, of any color.
        val empty = colors.minBy { luminance(it) }
        var bits = 0L
        for (cell in colors.indices) {
            if (distance(colors[cell], empty) > BLOCK_DISTANCE) bits = bits or (1L shl cell)
        }
        // Rows and columns that are still full are in the middle of being cleared.
        if (Board.fullLines(bits) != 0L) return null
        return Board(bits)
    }

    private fun readTray(scene: Scene, frame: Frame, geometry: BoardGeometry): List<TrayPiece>? {
        val shot = scene.shot
        val top = frame.bottom + (frame.height * TRAY_TOP).toInt()
        val bottom = minOf(shot.height - 1, frame.bottom + (frame.height * TRAY_BOTTOM).toInt())
        if (bottom <= top) return null
        val cell = geometry.cellSize * TRAY_SCALE

        val pieces = mutableListOf<TrayPiece>()
        for (slot in 0 until SLOTS) {
            // The pieces sit closer together than thirds of the screen: a line of five in an outer
            // slot reaches past a third. The borders lie halfway between the middles of the slots.
            val slotLeft = if (slot == 0) EDGE_X + 1 else frame.left + (frame.width * SLOT_BORDERS[slot - 1]).toInt()
            val slotRight =
                if (slot == SLOTS - 1) shot.width - 1 else frame.left + (frame.width * SLOT_BORDERS[slot]).toInt() - 1
            val box = boundsOfForeground(scene, slotLeft, top, slotRight, bottom) ?: continue

            // A piece touching the border of the area is still flying in, or it is something else.
            if (box.top == top || box.bottom == bottom || box.left == slotLeft || box.right == slotRight) return null
            val cols = (box.width / cell).roundToInt()
            val rows = (box.height / cell).roundToInt()
            if (cols !in 1..MAX_PIECE_SIZE || rows !in 1..MAX_PIECE_SIZE) return null
            if (abs(box.width - cols * cell) > cell * SIZE_TOLERANCE) return null
            if (abs(box.height - rows * cell) > cell * SIZE_TOLERANCE) return null

            val cells = mutableListOf<Pair<Int, Int>>()
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    val x = (box.left + (col + 0.5f) * cell).toInt()
                    val y = (box.top + (row + 0.5f) * cell).toInt()
                    if (scene.isForeground(x, y)) cells += row to col
                }
            }
            val piece = Piece.ofCells(cells.ifEmpty { return null })
            // Every row and column of the box must hold a block, or the box was not a piece.
            if (piece.width != cols || piece.height != rows) return null
            pieces += TrayPiece(slot, piece, box.left + cols * cell / 2, box.top + rows * cell / 2)
        }
        return pieces
    }

    /** The smallest box around the rows and columns of an area that clearly hold foreground. */
    private fun boundsOfForeground(scene: Scene, left: Int, top: Int, right: Int, bottom: Int): Frame? {
        val perColumn = IntArray(right - left + 1)
        val perRow = IntArray(bottom - top + 1)
        for (y in top..bottom) {
            for (x in left..right) {
                if (scene.isForeground(x, y)) {
                    perColumn[x - left]++
                    perRow[y - top]++
                }
            }
        }
        val firstColumn = perColumn.indexOfFirst { it >= MIN_LINE_PIXELS }
        val firstRow = perRow.indexOfFirst { it >= MIN_LINE_PIXELS }
        if (firstColumn < 0 || firstRow < 0) return null
        return Frame(
            left = left + firstColumn,
            top = top + firstRow,
            right = left + perColumn.indexOfLast { it >= MIN_LINE_PIXELS },
            bottom = top + perRow.indexOfLast { it >= MIN_LINE_PIXELS },
        )
    }

    private companion object {
        /** Column that is taken to show the background in every row. */
        const val EDGE_X = 4

        // The texture of the background is judged by pixels this far apart, every few rows.
        const val TEXTURE_REACH = 25
        const val TEXTURE_STEP = 3

        /** How much further than its typical texture the background is allowed to stray. */
        const val TEXTURE_FACTOR = 1.6f
        const val MIN_TOLERANCE = 36
        const val MAX_TOLERANCE = 100

        const val SCAN_STEP = 4
        const val COVERAGE = 0.9f

        // Fractions of the screen width.
        const val MAX_GAP = 0.03f
        const val MIN_BOARD_WIDTH = 0.6f

        const val SQUARE_TOLERANCE = 0.04f

        /** Width of the border around the grid, as a fraction of the board with its border. */
        const val FRAME_THICKNESS = 0.012f

        /** Distance of the color samples from the middle of a cell, in cells. */
        const val SAMPLE_SPREAD = 0.15f

        /** Color distance from an empty cell above which a cell holds a block. */
        const val BLOCK_DISTANCE = 90

        const val SLOTS = 3

        /** Where one slot ends and the next begins, as fractions of the board's width from its left. */
        val SLOT_BORDERS = floatArrayOf(0.34f, 0.66f)
        const val MAX_PIECE_SIZE = 5

        /** Size of a cell of an offered piece compared to a cell of the board. */
        const val TRAY_SCALE = 0.443f

        // The area searched for offered pieces, in board heights below the board.
        const val TRAY_TOP = 0.03f
        const val TRAY_BOTTOM = 0.56f

        const val SIZE_TOLERANCE = 0.3f
        const val MIN_LINE_PIXELS = 6

        /** The sum of the red, green and blue differences of two colors. */
        fun distance(a: Int, b: Int): Int =
            abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) +
                abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) +
                abs((a and 0xFF) - (b and 0xFF))

        fun luminance(pixel: Int): Int =
            ((pixel shr 16 and 0xFF) * 299 + (pixel shr 8 and 0xFF) * 587 + (pixel and 0xFF) * 114) / 1000
    }
}
