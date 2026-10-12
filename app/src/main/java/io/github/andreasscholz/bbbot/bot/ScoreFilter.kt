package io.github.andreasscholz.bbbot.bot

/**
 * Keeps misread scores out. The score of a game only grows, and by no more in one go than a
 * piece can earn, so a reading that fell or leapt is taken for a misreading and dropped.
 *
 * But what a piece can earn has no limit: it grows with the combo. A reading that looks wrong
 * is therefore believed after all when the next one agrees with it. Without that, one clear
 * that earned more than the leap allowed had every later reading dropped for the rest of the
 * game. The same lets a new game through, whose score starts from nothing again.
 */
class ScoreFilter(private val maxStep: Long = MAX_STEP) {

    private var believed = -1L
    private var doubted = -1L

    /** The score to go by after reading [read] off the screen, or null if there is none to trust. */
    fun accept(read: Long?): Long? {
        if (read == null) return null
        if (believed < 0 || follows(believed, read) || doubted >= 0 && follows(doubted, read)) {
            believed = read
            doubted = -1
            return read
        }
        doubted = read
        return null
    }

    private fun follows(earlier: Long, later: Long) = later >= earlier && later - earlier <= maxStep

    companion object {
        /** More than a piece earns in all but the longest combos. */
        const val MAX_STEP = 2_000_000L
    }
}
