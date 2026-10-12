package io.github.andreasscholz.bbbot.game

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoringTest {

    @Test
    fun `a clear earns what the game was measured to pay`() {
        // The start of a combo as it was traced, less the point or two that comes on top.
        assertEquals(listOf(50L, 100L, 150L, 200L, 250L, 450L), (1..6).map { Scoring.clear(1, it, false) })
        assertEquals(750L, Scoring.clear(1, 10, false))
        assertEquals(1100L, Scoring.clear(1, 11, false))
        assertEquals(2 * 3300L, Scoring.clear(2, 33, false))
        assertEquals(6 * 3300L, Scoring.clear(3, 33, false))
    }

    @Test
    fun `emptying the board adds the same few points at any length of the combo`() {
        assertEquals(300L, Scoring.clear(1, 1, true) - Scoring.clear(1, 1, false))
        assertEquals(300L, Scoring.clear(2, 150, true) - Scoring.clear(2, 150, false))
    }
}
