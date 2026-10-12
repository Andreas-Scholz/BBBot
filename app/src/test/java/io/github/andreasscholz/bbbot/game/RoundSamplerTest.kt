package io.github.andreasscholz.bbbot.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundSamplerTest {

    private val sampler = RoundSampler(samples = 200, seed = 1)

    @Test
    fun `every round fits on an empty board, but few can clear a line there`() {
        val chances = sampler.chances(Board.EMPTY.bits)
        assertEquals(1.0, chances.fit, 0.0)
        assertTrue("clear ${chances.clear}", chances.clear < 0.5)
    }

    @Test
    fun `a line that lacks one block is cleared by many more rounds`() {
        val board = Board.parse(*Array(7) { "........" }, "XXXXXXX.")
        val chances = sampler.chances(board.bits)
        assertEquals(1.0, chances.fit, 0.0)
        assertTrue("clear ${chances.clear}", chances.clear > 0.5)
        assertTrue(chances.clear > sampler.chances(Board.EMPTY.bits).clear + 0.2)
    }

    @Test
    fun `hardly any round fits on a board with single cells left`() {
        val checkered = Board.parse(*Array(8) { if (it % 2 == 0) "X.X.X.X." else ".X.X.X.X" })
        val chances = sampler.chances(checkered.bits)
        assertTrue("fit ${chances.fit}", chances.fit < 0.1)
        assertEquals(0.0, chances.clear, 0.0)
    }

    @Test
    fun `the same seed tries the same rounds`() {
        val board = Board.parse(*Array(6) { "........" }, "XXXX....", "XXXXXX..")
        val first = RoundSampler(samples = 50, seed = 7).chances(board.bits)
        val second = RoundSampler(samples = 50, seed = 7).chances(board.bits)
        assertEquals(first.clear, second.clear, 0.0)
        assertEquals(first.fit, second.fit, 0.0)
    }
}
