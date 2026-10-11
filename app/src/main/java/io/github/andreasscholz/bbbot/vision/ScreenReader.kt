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

/**
 * Where the board was found beyond doubt on a screen of [screenWidth] by [screenHeight] pixels:
 * its outline with the border and its grid. The game keeps the board in the same place in
 * every theme, so this serves when a later screenshot does not show the board clearly.
 */
data class KnownBoard(
    val screenWidth: Int,
    val screenHeight: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val gridLeft: Float,
    val gridTop: Float,
    val cellSize: Float,
) {
    /** A line of text to keep it in, see [decode]. */
    fun encode(): String = listOf(screenWidth, screenHeight, left, top, right, bottom, gridLeft, gridTop, cellSize).joinToString(",")

    companion object {
        fun decode(text: String?): KnownBoard? {
            val parts = text?.split(",") ?: return null
            if (parts.size != 9) return null
            return try {
                KnownBoard(
                    parts[0].toInt(), parts[1].toInt(), parts[2].toInt(), parts[3].toInt(), parts[4].toInt(),
                    parts[5].toInt(), parts[6].toFloat(), parts[7].toFloat(), parts[8].toFloat(),
                )
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
}

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
class ScreenReader(
    /** Told why a screenshot was not taken for a game, to find out what a new theme breaks. */
    private val onReject: (String) -> Unit = {},
) {

    /**
     * The board as it was last found beyond doubt, or as given from an earlier run. Blocks in
     * the color of the background, pink on pink for example, can hide so much of the board that
     * it cannot be found by looking; it is then taken to be where it was, as long as enough of
     * that place still looks like a board.
     */
    var known: KnownBoard? = null

    private fun <T> reject(reason: String): T? {
        onReject(reason)
        return null
    }

    /** Returns null when the screenshot does not show a game waiting for a move. */
    fun read(shot: Screenshot): GameScreen? {
        val scene = Scene(shot)
        val found = findFrame(scene) ?: return null
        val frame = found.frame
        val remembered = found.remembered
        val geometry = if (remembered != null) {
            BoardGeometry(remembered.gridLeft, remembered.gridTop, remembered.cellSize)
        } else {
            // The grid is where the border ends, and the themes draw borders of different widths.
            val borderX = borderThickness(shot, frame, alongRows = true)
            val borderY = borderThickness(shot, frame, alongRows = false)
            BoardGeometry(
                left = (frame.left + borderX).toFloat(),
                top = (frame.top + borderY).toFloat(),
                cellSize = (frame.width - 2 * borderX) / Board.SIZE.toFloat(),
            )
        }
        val board = readBoard(shot, geometry) ?: return null
        val tray = readTray(scene, frame, geometry) ?: return null
        if (found.exact) {
            known = KnownBoard(
                shot.width, shot.height, frame.left, frame.top, frame.right, frame.bottom,
                geometry.left, geometry.top, geometry.cellSize,
            )
        }
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

        /**
         * The background color of every row: the median of the left edge a few rows up and down.
         * A single pixel will not do, because a vein of marble that crosses the edge would turn
         * its whole row into foreground.
         */
        private val background = IntArray(shot.height) { y ->
            val from = (y - EDGE_REACH).coerceAtLeast(0)
            val to = (y + EDGE_REACH).coerceAtMost(shot.height - 1)
            val count = to - from + 1
            val red = IntArray(count) { shot[EDGE_X, from + it] shr 16 and 0xFF }
            val green = IntArray(count) { shot[EDGE_X, from + it] shr 8 and 0xFF }
            val blue = IntArray(count) { shot[EDGE_X, from + it] and 0xFF }
            red.sort()
            green.sort()
            blue.sort()
            (red[count / 2] shl 16) or (green[count / 2] shl 8) or blue[count / 2]
        }

        init {
            val distances = IntArray((shot.height - TEXTURE_REACH) / TEXTURE_STEP) {
                val y = it * TEXTURE_STEP
                distance(shot[EDGE_X - 2, y], shot[EDGE_X + 2, y + TEXTURE_REACH])
            }
            distances.sort()
            val typical = distances[distances.size * 99 / 100]
            tolerance = (typical * TEXTURE_FACTOR).toInt().coerceIn(MIN_TOLERANCE, MAX_TOLERANCE)
        }

        fun isForeground(x: Int, y: Int): Boolean = distance(shot[x, y], background[y]) > tolerance
    }

    private class Frame(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left + 1
        val height: Int get() = bottom - top + 1
    }

    /**
     * A board that was found: [exact] when it came out square by itself, [remembered] when it
     * could not be found and was taken from [known] instead.
     */
    private class Found(val frame: Frame, val exact: Boolean, val remembered: KnownBoard? = null)

    /** [known], if enough of the rows it covers still look like rows of a board. */
    private fun remembered(shot: Screenshot, boardRows: BooleanArray): Found? {
        val board = known ?: return null
        if (board.screenWidth != shot.width || board.screenHeight != shot.height) return null
        val rows = (board.top..board.bottom).count { boardRows[it] }
        if (rows < (board.bottom - board.top + 1) * MIN_SUPPORT) return null
        return Found(Frame(board.left, board.top, board.right, board.bottom), exact = false, remembered = board)
    }

    /**
     * The board with its border: the tallest band that is not background nearly all the way
     * across. Rows of blocks in the color of the background are only partly "not background";
     * they may be part of the band, but cannot make one up: a good share of the band's rows has
     * to be covered nearly all the way.
     */
    private fun findFrame(scene: Scene): Found? {
        val shot = scene.shot
        val from = (shot.width * 0.1f).toInt()
        val to = (shot.width * 0.9f).toInt()
        val needed = ((to - from) / SCAN_STEP * COVERAGE).toInt()
        val neededPartly = ((to - from) / SCAN_STEP * PARTLY_COVERED).toInt()

        var bestTop = 0
        var bestBottom = -1
        var runTop = -1
        var lastHit = -1
        var coveredInRun = 0
        val boardRows = BooleanArray(shot.height)
        for (y in 0 until shot.height) {
            var hits = 0
            var x = from
            while (x < to) {
                if (scene.isForeground(x, y)) hits++
                x += SCAN_STEP
            }
            if (hits >= neededPartly) {
                // The shaded edges of blocks look like the background, so short gaps are bridged.
                if (runTop < 0 || y - lastHit > shot.width * MAX_GAP) {
                    runTop = y
                    coveredInRun = 0
                }
                lastHit = y
                if (hits >= needed) {
                    boardRows[y] = true
                    coveredInRun++
                }
                val length = lastHit - runTop + 1
                if (length > bestBottom - bestTop + 1 && coveredInRun >= length * MIN_SUPPORT) {
                    bestTop = runTop
                    bestBottom = lastHit
                }
            }
        }
        val height = bestBottom - bestTop + 1
        if (height < shot.width * MIN_BOARD_WIDTH) {
            return remembered(shot, boardRows) ?: reject("no board: the tallest band is $height pixels")
        }

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
        if (left < 0) return remembered(shot, boardRows) ?: reject("no board: no column of the band is covered")
        val band = Frame(left, bestTop, right, bestBottom)
        if (abs(band.width - band.height) <= band.width * SQUARE_TOLERANCE) return Found(band, exact = true)

        // Not square: part of the board was taken for background. Where it was is the best guess,
        // working out what is missing the second best.
        remembered(shot, boardRows)?.let { return it }
        val frame = completed(scene, band)
        if (abs(frame.width - frame.height) > frame.width * SQUARE_TOLERANCE) {
            return reject("board is not square: ${frame.width} x ${frame.height}")
        }
        return Found(frame, exact = false)
    }

    /**
     * Makes up for edges of the board that were taken for background. A row or column of blocks
     * in the color of the background, pink blocks on pink for example, ends the band early, on
     * one side or on both. The board is square, so what is missing is as wide as the difference
     * of the sides; it is split between the two sides in the way that takes in the most pixels
     * that are not background.
     */
    private fun completed(scene: Scene, frame: Frame): Frame {
        val missing = abs(frame.width - frame.height)
        val longer = maxOf(frame.width, frame.height)
        if (missing <= longer * SQUARE_TOLERANCE || missing > longer * MAX_MISSING) return frame
        val tooShort = frame.height < frame.width

        /** The foreground in the line of pixels [distance] outside the frame, before it or after it. */
        fun foreground(distance: Int, before: Boolean): Int {
            val line = if (tooShort) {
                if (before) frame.top - distance else frame.bottom + distance
            } else {
                if (before) frame.left - distance else frame.right + distance
            }
            val limit = if (tooShort) scene.shot.height else scene.shot.width
            if (line < 0 || line >= limit) return Int.MIN_VALUE / 2
            var hits = 0
            if (tooShort) {
                for (x in frame.left..frame.right step SCAN_STEP) if (scene.isForeground(x, line)) hits++
            } else {
                for (y in frame.top..frame.bottom step SCAN_STEP) if (scene.isForeground(line, y)) hits++
            }
            return hits
        }

        // With `before` lines added in front, `missing - before` are added behind.
        var behind = (1..missing).sumOf { foreground(it, before = false) }
        var inFront = 0
        var best = behind
        var bestBefore = 0
        for (before in 1..missing) {
            inFront += foreground(before, before = true)
            behind -= foreground(missing - before + 1, before = false)
            if (inFront + behind > best) {
                best = inFront + behind
                bestBefore = before
            }
        }
        val after = missing - bestBefore
        return if (tooShort) {
            Frame(frame.left, frame.top - bestBefore, frame.right, frame.bottom + after)
        } else {
            Frame(frame.left - bestBefore, frame.top, frame.right + after, frame.bottom)
        }
    }

    /**
     * How many pixels the border of the board is wide: how far the color just inside its outer
     * edge reaches inwards. Looked up at many places along the left edge (or the top edge) and
     * settled by the median, since a single place may hit something odd.
     */
    private fun borderThickness(shot: Screenshot, frame: Frame, alongRows: Boolean): Int {
        val limit = (frame.width * MAX_BORDER).toInt()
        val samples = IntArray(BORDER_SAMPLES) { sample ->
            val offset = ((sample + 0.5f) / BORDER_SAMPLES * frame.width).toInt()
            fun pixel(depth: Int): Int =
                if (alongRows) shot[frame.left + depth, frame.top + offset] else shot[frame.left + offset, frame.top + depth]

            // The outermost pixel is blended with the background, so the color is taken one further in.
            val color = pixel(1)
            var depth = 2
            while (depth < limit && distance(pixel(depth), color) < BORDER_DISTANCE) depth++
            depth
        }
        samples.sort()
        return samples[BORDER_SAMPLES / 2]
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
        // A full row is no reason to doubt the board: a game can begin with one. Rows that are in
        // the middle of being cleared are told apart by whoever looks twice.
        return Board(bits)
    }

    private fun readTray(scene: Scene, frame: Frame, geometry: BoardGeometry): List<TrayPiece>? {
        val shot = scene.shot
        val top = frame.bottom + (frame.height * TRAY_TOP).toInt()
        val bottom = minOf(shot.height - 1, frame.bottom + (frame.height * TRAY_BOTTOM).toInt())
        if (bottom <= top) return reject("no room for pieces below the board")
        val cell = geometry.cellSize * TRAY_SCALE

        val pieces = mutableListOf<TrayPiece>()
        for (slot in 0 until SLOTS) {
            // The pieces sit closer together than thirds of the screen: a line of five in an outer
            // slot reaches past a third. The borders lie halfway between the middles of the slots.
            // No piece reaches beyond the board, and the far edge of the screen is not searched
            // because a background that gets darker towards it no longer matches the left edge.
            val slotLeft = frame.left + (frame.width * SLOT_BORDERS[slot]).toInt()
            val slotRight = frame.left + (frame.width * SLOT_BORDERS[slot + 1]).toInt() - 1
            val box = boundsOfForeground(scene, slotLeft, top, slotRight, bottom, (cell * MIN_LINE).toInt()) ?: continue

            // A piece touching the border of the area is still flying in, or it is something else.
            if (box.top == top || box.bottom == bottom || box.left == slotLeft || box.right == slotRight) {
                return reject("slot $slot: piece touches the border of its area")
            }
            val cols = (box.width / cell).roundToInt()
            val rows = (box.height / cell).roundToInt()
            val size = "${box.width} x ${box.height} pixels with cells of $cell"
            if (cols !in 1..MAX_PIECE_SIZE || rows !in 1..MAX_PIECE_SIZE) return reject("slot $slot: piece of $size")
            if (abs(box.width - cols * cell) > cell * SIZE_TOLERANCE || abs(box.height - rows * cell) > cell * SIZE_TOLERANCE) {
                return reject("slot $slot: not whole cells, $size")
            }

            val cells = mutableListOf<Pair<Int, Int>>()
            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    if (holdsBlock(scene, box.left + col * cell, box.top + row * cell, cell)) cells += row to col
                }
            }
            val piece = Piece.ofCells(cells.ifEmpty { return reject("slot $slot: no blocks in the box") })
            // Every row and column of the box must hold a block, or the box was not a piece.
            if (piece.width != cols || piece.height != rows) return reject("slot $slot: box of $cols x $rows cells holds\n$piece")
            pieces += TrayPiece(slot, piece, box.left + cols * cell / 2, box.top + rows * cell / 2)
        }
        return pieces
    }

    /**
     * Whether the cell of a piece with its top left corner at [left], [top] holds a block. A
     * single pixel in the middle is not enough to tell: a block of a patterned theme has spots
     * in the color of the background. The outer quarter of the cell is left out because the
     * block next to it throws its shadow there.
     */
    private fun holdsBlock(scene: Scene, left: Float, top: Float, cell: Float): Boolean {
        var hits = 0
        for (row in 0 until CELL_SAMPLES) {
            for (col in 0 until CELL_SAMPLES) {
                val x = left + cell * (0.25f + 0.5f * col / (CELL_SAMPLES - 1))
                val y = top + cell * (0.25f + 0.5f * row / (CELL_SAMPLES - 1))
                if (scene.isForeground(x.toInt(), y.toInt())) hits++
            }
        }
        return hits >= CELL_SAMPLES * CELL_SAMPLES * MIN_CELL_FILL
    }

    /**
     * The smallest box around the rows and columns of an area that hold at least [minPixels]
     * pixels of foreground. A textured background scatters single foreground pixels everywhere,
     * a piece fills at least the length of a cell.
     */
    private fun boundsOfForeground(scene: Scene, left: Int, top: Int, right: Int, bottom: Int, minPixels: Int): Frame? {
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
        val firstColumn = perColumn.indexOfFirst { it >= minPixels }
        val firstRow = perRow.indexOfFirst { it >= minPixels }
        if (firstColumn < 0 || firstRow < 0) return null
        return Frame(
            left = left + firstColumn,
            top = top + firstRow,
            right = left + perColumn.indexOfLast { it >= minPixels },
            bottom = top + perRow.indexOfLast { it >= minPixels },
        )
    }

    private companion object {
        /** Column that is taken to show the background in every row. */
        const val EDGE_X = 4

        /** How many rows up and down the background color of a row is taken from. */
        const val EDGE_REACH = 8

        // The texture of the background is judged by pixels this far apart, every few rows.
        const val TEXTURE_REACH = 25
        const val TEXTURE_STEP = 3

        /** How much further than its typical texture the background is allowed to stray. */
        const val TEXTURE_FACTOR = 1.6f
        const val MIN_TOLERANCE = 36
        const val MAX_TOLERANCE = 100

        const val SCAN_STEP = 4
        const val COVERAGE = 0.9f

        /** The part of a row that must be foreground for it to possibly belong to the board. */
        const val PARTLY_COVERED = 0.3f

        // Fractions of the screen width.
        const val MAX_GAP = 0.03f
        const val MIN_BOARD_WIDTH = 0.6f

        const val SQUARE_TOLERANCE = 0.04f

        /** The part of a board's rows that must be covered nearly all the way to take it for one. */
        const val MIN_SUPPORT = 0.35f

        /** The most of a side that may be missing and made up for, as a fraction of the side. */
        const val MAX_MISSING = 0.4f

        const val BORDER_SAMPLES = 31

        /** Color distance within which a pixel still belongs to the border. */
        const val BORDER_DISTANCE = 30

        /** The widest border looked for, as a fraction of the board's width. */
        const val MAX_BORDER = 0.03f

        /** Distance of the color samples from the middle of a cell, in cells. */
        const val SAMPLE_SPREAD = 0.15f

        /** Color distance from an empty cell above which a cell holds a block. */
        const val BLOCK_DISTANCE = 90

        const val SLOTS = 3

        /** Where one slot ends and the next begins, as fractions of the board's width from its left. */
        val SLOT_BORDERS = floatArrayOf(0f, 0.34f, 0.66f, 1f)
        const val MAX_PIECE_SIZE = 5

        /** Size of a cell of an offered piece compared to a cell of the board. */
        const val TRAY_SCALE = 0.447f

        // The area searched for offered pieces, in board heights below the board.
        const val TRAY_TOP = 0.03f
        const val TRAY_BOTTOM = 0.56f

        /** How far the box around a piece may be from whole cells; shadows make it larger. */
        const val SIZE_TOLERANCE = 0.4f
        /** A cell of a piece is looked at in this many places per direction. */
        const val CELL_SAMPLES = 5

        /** The part of those places that must be foreground for the cell to hold a block. */
        const val MIN_CELL_FILL = 0.3f

        /** The foreground a row or column of pixels needs to belong to a piece, in cells of the piece. */
        const val MIN_LINE = 0.4f

        /** The sum of the red, green and blue differences of two colors. */
        fun distance(a: Int, b: Int): Int =
            abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) +
                abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) +
                abs((a and 0xFF) - (b and 0xFF))

        fun luminance(pixel: Int): Int =
            ((pixel shr 16 and 0xFF) * 299 + (pixel shr 8 and 0xFF) * 587 + (pixel and 0xFF) * 114) / 1000
    }
}
