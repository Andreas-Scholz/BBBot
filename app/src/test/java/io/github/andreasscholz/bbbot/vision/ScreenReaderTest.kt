package io.github.andreasscholz.bbbot.vision

import io.github.andreasscholz.bbbot.game.Board
import io.github.andreasscholz.bbbot.game.Piece
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import javax.imageio.ImageIO

/** Reads real screenshots taken on a 1080×2400 phone; see `src/test/resources/screens`. */
class ScreenReaderTest {

    private val reader = ScreenReader()

    @Test
    fun `reads the board and three pieces`() {
        val screen = reader.read(load("wood-start.png"))!!

        assertEquals(
            Board.parse(
                "XXXX.X.X",
                ".X.X.XX.",
                "X.XXXX..",
                ".XXXX...",
                "X.XX...X",
                ".XX.XXXX",
                "X...X...",
                "XXX....X",
            ),
            screen.board,
        )
        assertEquals(
            listOf(Piece.of("XX", "XX"), Piece.of("..X", "XXX"), Piece.of(".X.", "XXX")),
            screen.tray.map { it.piece },
        )
        assertEquals(listOf(0, 1, 2), screen.tray.map { it.slot })
    }

    @Test
    fun `finds the grid and the middle of the pieces`() {
        val screen = reader.read(load("wood-start.png"))!!

        assertEquals(61f, screen.geometry.left, 4f)
        assertEquals(580f, screen.geometry.top, 4f)
        assertEquals(119.7f, screen.geometry.cellSize, 1f)
        for ((piece, x) in screen.tray.zip(listOf(225f, 540f, 855f))) {
            assertEquals(x, piece.centerX, 4f)
            assertEquals(1818f, piece.centerY, 4f)
        }
    }

    @Test
    fun `leaves out the slot of a piece that was placed`() {
        val screen = reader.read(load("wood-two-pieces-left.png"))!!

        assertEquals(
            Board.parse(
                "XXXX.X.X",
                ".X.X.XX.",
                "X.XXXX..",
                ".XXXXXX.",
                "X.XX.XXX",
                ".XX.XXXX",
                "X...X...",
                "XXX....X",
            ),
            screen.board,
        )
        assertEquals(listOf(1, 2), screen.tray.map { it.slot })
        assertEquals(listOf(Piece.of("..X", "XXX"), Piece.of(".X.", "XXX")), screen.tray.map { it.piece })
    }

    @Test
    fun `reads the marble theme with its veined background and the panel on top`() {
        val screen = reader.read(load("marble-with-panel.png"))!!

        assertEquals(
            Board.parse(
                "XXXX...X",
                ".X.X..X.",
                "X.XXX...",
                ".XXXX.X.",
                "X.XX..XX",
                ".XXXX.XX",
                "XXXXX...",
                "XXX.X.XX",
            ),
            screen.board,
        )
        assertEquals(
            listOf(Piece.of(".X", "XX", ".X"), Piece.of("X.", ".X"), Piece.of("X.", ".X")),
            screen.tray.map { it.piece },
        )
        assertEquals(61f, screen.geometry.left, 4f)
        assertEquals(580f, screen.geometry.top, 4f)
        assertEquals(1818f, screen.tray[0].centerY, 4f)
    }

    @Test
    fun `reads the pink theme`() {
        val screen = reader.read(load("pink-with-panel.png"))!!

        assertEquals(
            Board.parse(
                "XX.XXXXX",
                ".XXXXXXX",
                "........",
                "........",
                ".XX...XX",
                "......XX",
                "...X...X",
                "........",
            ),
            screen.board,
        )
        val square = Piece.of("XX", "XX")
        assertEquals(listOf(square, square, Piece.of("XXX", "XXX", "XXX")), screen.tray.map { it.piece })
    }

    @Test
    fun `reads lines of five, which reach beyond a third of the screen`() {
        val screen = reader.read(load("pink-lines-of-five.png"))!!

        assertEquals(
            Board.parse(
                "X..XXXXX",
                "........",
                "XXX.....",
                "..X.....",
                "..X...XX",
                "X.X...XX",
                "X.XX...X",
                "X.X.....",
            ),
            screen.board,
        )
        assertEquals(
            listOf(Piece.of("XXXXX"), Piece.of(".XX", "XX."), Piece.of("X", "X", "X", "X", "X")),
            screen.tray.map { it.piece },
        )
        assertEquals(1818f, screen.tray[2].centerY, 4f)
    }

    @Test
    fun `reads the purple theme with its thin border and colored blocks`() {
        val screen = reader.read(load("purple-thin-border.png"))!!

        assertEquals(
            Board.parse(
                "........",
                "........",
                "........",
                "........",
                "......XX",
                ".....XXX",
                ".....XXX",
                ".....XXX",
            ),
            screen.board,
        )
        val four = Piece.of("X", "X", "X", "X")
        assertEquals(listOf(four, four, Piece.of("X", "X", "X", "X", "X")), screen.tray.map { it.piece })
        // The grid is where it is in the other themes, although the border is much thinner. It is
        // so faint that it passes for background, which costs a few pixels of accuracy.
        assertEquals(61f, screen.geometry.left, 6f)
        assertEquals(574f, screen.geometry.top, 4f)
        assertEquals(119.7f, screen.geometry.cellSize, 1.5f)
    }

    @Test
    fun `reads an empty board on a furry background`() {
        val screen = reader.read(load("fur-empty-board.png"))!!

        assertEquals(Board.EMPTY, screen.board)
        assertEquals(
            listOf(Piece.of("XXXXX"), Piece.of(".X", ".X", "XX"), Piece.of(".X", "XX", ".X")),
            screen.tray.map { it.piece },
        )
        assertEquals(61f, screen.geometry.left, 4f)
        assertEquals(580f, screen.geometry.top, 4f)
    }

    @Test
    fun `reads a checkered piece in the color of the background`() {
        val screen = reader.read(load("gingham-pink-piece.png"))!!

        assertEquals(
            Board.parse(
                "........",
                "........",
                ".......X",
                ".......X",
                "........",
                "X...X...",
                "XX..X.XX",
                "XX...XXX",
            ),
            screen.board,
        )
        // The middle of the corner block lies on a stripe that looks like the background.
        assertEquals(listOf(Piece.of("..X", "..X", "XXX")), screen.tray.map { it.piece })
        assertEquals(listOf(2), screen.tray.map { it.slot })
    }

    @Test
    fun `is not misled by a vein of marble that crosses the edge of the screen`() {
        // Below the line of five a dark vein runs into the left edge, where the background is read.
        val screen = reader.read(load("grey-marble-vein.png"))!!

        assertEquals(Board.EMPTY, screen.board)
        assertEquals(listOf(Piece.of("XXXXX")), screen.tray.map { it.piece })
        assertEquals(1818f, screen.tray[0].centerY, 4f)
    }

    @Test
    fun `finds the whole board when its bottom row is filled in the color of the background`() {
        // Pink blocks on pink: the bottom row is taken for background and the board for too short.
        val screen = reader.read(load("gingham-pink-bottom-row.png"))!!

        assertEquals(
            Board.parse(
                "........",
                "........",
                "........",
                "........",
                "........",
                "XXX.....",
                "XXX.....",
                "XXX.XXXX",
            ),
            screen.board,
        )
        assertEquals(listOf(Piece.of("XXX", "X..", "X..")), screen.tray.map { it.piece })
        assertEquals(580f, screen.geometry.top, 4f)
        assertEquals(119.7f, screen.geometry.cellSize, 1f)
    }

    @Test
    fun `finds the whole board when its top and bottom rows are filled in the color of the background`() {
        val screen = reader.read(load("gingham-pink-top-and-bottom.png"))!!

        assertEquals(
            Board.parse(
                "XXXXX...",
                "XXX.....",
                "XXX.....",
                "........",
                "........",
                "XXX.....",
                "XXX.....",
                "XXX.XXXX",
            ),
            screen.board,
        )
        assertEquals(listOf(Piece.of("X", "X", "X", "X"), Piece.of("XXX", "XXX")), screen.tray.map { it.piece })
        assertEquals(580f, screen.geometry.top, 4f)
    }

    @Test
    fun `reads a board with rows inside it in the color of the background`() {
        // Two rows inside the board are mostly pink on pink, which used to break it into bands.
        val screen = reader.read(load("gingham-pink-rows-inside.png"))!!

        assertEquals(
            Board.parse(
                "XXXXX...",
                "........",
                "XXXXX...",
                "........",
                ".......X",
                ".......X",
                ".......X",
                "XXXX...X",
            ),
            screen.board,
        )
        assertEquals(listOf(Piece.of("XXX", "X..", "X.."), Piece.of(".X", "XX", ".X")), screen.tray.map { it.piece })
        assertEquals(listOf(0, 2), screen.tray.map { it.slot })
    }

    @Test
    fun `takes the board to be where it was when it cannot be found`() {
        // Stripes in the color of the background along the edges of every row of cells cut the
        // board into bands too thin to be one, without touching the middle of any cell.
        val original = load("wood-start.png")
        val hidden = Screenshot(original.width, original.height, original.pixels.copyOf())
        for (row in 0 until 8) {
            val top = 580 + (row * 119.7).toInt()
            for (y in (top until top + 35) + (top + 85 until top + 120)) {
                for (x in 100 until 980) hidden.pixels[y * hidden.width + x] = original[4, y]
            }
        }
        assertNull(ScreenReader().read(hidden))

        val reader = ScreenReader()
        val before = reader.read(original)!!
        val screen = reader.read(hidden)!!
        assertEquals(before.geometry, screen.geometry)
        assertEquals(before.tray, screen.tray)
    }

    @Test
    fun `knowing where the board was does not make a board out of anything`() {
        val reader = ScreenReader()
        reader.read(load("wood-start.png"))!!
        assertNull(reader.read(Screenshot(1080, 2400, IntArray(1080 * 2400) { 0xFF336699.toInt() })))
    }

    @Test
    fun `what is known about the board survives being written down`() {
        val reader = ScreenReader()
        reader.read(load("wood-start.png"))!!
        val known = reader.known!!
        assertEquals(known, KnownBoard.decode(known.encode()))
        assertNull(KnownBoard.decode("not a board"))
        assertNull(KnownBoard.decode(null))
    }

    @Test
    fun `reads the same game in other colors`() {
        // The colors change with the theme of the game; swapping the channels imitates another one.
        val original = load("wood-start.png")
        val recolored = Screenshot(
            original.width,
            original.height,
            IntArray(original.pixels.size) {
                val pixel = original.pixels[it]
                (pixel and 0xFF shl 16) or (pixel shr 16 and 0xFF shl 8) or (pixel shr 8 and 0xFF)
            },
        )
        assertEquals(reader.read(original), reader.read(recolored))
    }

    @Test
    fun `sees no game on a blank screen`() {
        assertNull(reader.read(Screenshot(1080, 2400, IntArray(1080 * 2400) { 0xFF336699.toInt() })))
    }

    private fun load(name: String): Screenshot {
        val image = ImageIO.read(checkNotNull(javaClass.getResource("/screens/$name")) { "Missing $name" })
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        return Screenshot(image.width, image.height, pixels)
    }
}
