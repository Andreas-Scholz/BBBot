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
    fun `the length of the combo is read off what a clear earned`() {
        // As traced: the first clears of a combo, and clears deep in one.
        assertEquals(listOf(1, 2, 5, 6, 10, 11, 13), listOf(51L, 101L, 251L, 452L, 753L, 1104L, 1306L).map { Scoring.comboLength(it, 1, false) })
        assertEquals(364, Scoring.comboLength(36520, 1, false))
        assertEquals(366, Scoring.comboLength(36722, 1, false))
        assertEquals(33, Scoring.comboLength(6922, 2, true))
        assertEquals(26, Scoring.comboLength(15652, 3, false))
        // Not what any combo pays, or more lines than were measured often enough.
        assertEquals(null, Scoring.comboLength(36560, 1, false))
        assertEquals(null, Scoring.comboLength(43353, 4, false))
    }

    @Test
    fun `emptying the board adds the same few points at any length of the combo`() {
        assertEquals(300L, Scoring.clear(1, 1, true) - Scoring.clear(1, 1, false))
        assertEquals(300L, Scoring.clear(2, 150, true) - Scoring.clear(2, 150, false))
    }
}
