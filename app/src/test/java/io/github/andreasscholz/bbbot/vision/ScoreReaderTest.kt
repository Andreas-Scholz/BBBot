package io.github.andreasscholz.bbbot.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.imageio.ImageIO

/**
 * Reads the strips of real screenshots in `src/test/resources/scores`. Each file is the part of
 * a 1080×2400 screen where the score stands, named after the score it shows.
 */
class ScoreReaderTest {

    @Test
    fun `reads scores of every length, in several themes, over the combo symbol`() {
        // The last one has nine digits and the rainbow heart of a long combo behind them.
        val scores = listOf(0L, 17942242L, 17942253L, 18162720L, 23350136L, 23555286L, 25195962L, 25258059L, 180215939L)
        for (score in scores) {
            assertEquals(score, ScoreReader.read(screenWith("$score.png")))
        }
    }

    @Test
    fun `is not misled by a combo symbol with a white rim`() {
        // The fried egg behind the score: its rim was taken for part of the digits.
        assertEquals(25890504L, ScoreReader.read(screenWith("25890504.png")))
    }

    @Test
    fun `gives no score rather than a doubtful one`() {
        // Caught while the digit was still growing into place, it matches its pattern badly.
        val read = ScoreReader.read(screenWith("4.png"))
        assertTrue("read $read", read == null || read == 4L)
    }

    @Test
    fun `reads nothing where there is no score`() {
        assertNull(ScoreReader.read(Screenshot(WIDTH, HEIGHT, IntArray(WIDTH * HEIGHT) { 0xFF336699.toInt() })))
        assertNull(ScoreReader.read(Screenshot(WIDTH, HEIGHT, IntArray(WIDTH * HEIGHT) { 0xFFFFFFFF.toInt() })))
    }

    /** A screen that is empty but for the strip with the score. */
    private fun screenWith(name: String): Screenshot {
        val strip = ImageIO.read(checkNotNull(javaClass.getResource("/scores/$name")) { "Missing $name" })
        val pixels = IntArray(WIDTH * HEIGHT) { strip.getRGB(0, 0) }
        val top = (HEIGHT * ScoreReader.TOP).toInt()
        strip.getRGB(0, 0, strip.width, strip.height, pixels, top * WIDTH, WIDTH)
        return Screenshot(WIDTH, HEIGHT, pixels)
    }

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 2400
    }
}
