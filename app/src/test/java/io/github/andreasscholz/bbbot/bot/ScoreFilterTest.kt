package io.github.andreasscholz.bbbot.bot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoreFilterTest {

    @Test
    fun `a score that fell or leapt once is dropped`() {
        val filter = ScoreFilter(maxStep = 1000)
        assertEquals(5000L, filter.accept(5000))
        assertNull(filter.accept(4000))
        assertEquals(5200L, filter.accept(5200))
        assertNull(filter.accept(52000))
        assertNull(filter.accept(null))
        assertEquals(5300L, filter.accept(5300))
    }

    @Test
    fun `a leap is believed when the next reading agrees with it`() {
        // A clear in a long combo earned more than the leap allowed, and no score was read again.
        val filter = ScoreFilter(maxStep = 1000)
        assertEquals(5000L, filter.accept(5000))
        assertNull(filter.accept(9000))
        assertEquals(9100L, filter.accept(9100))
        assertEquals(9100L, filter.accept(9100))
    }

    @Test
    fun `a new game starts from nothing`() {
        val filter = ScoreFilter(maxStep = 1000)
        assertEquals(5000L, filter.accept(5000))
        assertNull(filter.accept(0))
        assertEquals(9L, filter.accept(9))
    }
}
