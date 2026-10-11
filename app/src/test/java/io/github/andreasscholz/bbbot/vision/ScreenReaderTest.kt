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

        assertEquals(61f, screen.geometry.left, 2f)
        assertEquals(580f, screen.geometry.top, 2f)
        assertEquals(119.7f, screen.geometry.cellSize, 0.5f)
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
        assertEquals(61f, screen.geometry.left, 2f)
        assertEquals(580f, screen.geometry.top, 2f)
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
