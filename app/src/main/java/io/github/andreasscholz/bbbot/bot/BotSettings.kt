package io.github.andreasscholz.bbbot.bot

import android.content.Context
import androidx.core.content.edit
import io.github.andreasscholz.bbbot.game.Strategy
import io.github.andreasscholz.bbbot.vision.KnownBoard

/** The combo at a score: its length, whether the round then had its clear, and the longest it has been in the game. */
data class SavedCombo(val length: Int, val clearedThisRound: Boolean, val score: Long, val longest: Int = length)

/** What the user chose in the app, and what the bot keeps, across restarts. */
class BotSettings(context: Context) {

    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** How the bot plays. Read when the bot is started, so a change applies to the next start. */
    var strategy: Strategy
        get() = preferences.getString(KEY_STRATEGY, null)
            ?.let { name -> Strategy.entries.find { it.name == name } }
            ?: Strategy.BALANCED
        set(value) = preferences.edit { putString(KEY_STRATEGY, value.name) }

    /** Where the board is on the screen, as the recognition last found it; see [KnownBoard]. */
    var knownBoard: KnownBoard?
        get() = KnownBoard.decode(preferences.getString(KEY_KNOWN_BOARD, null))
        set(value) = preferences.edit { putString(KEY_KNOWN_BOARD, value?.encode()) }

    /**
     * Whether the bot waits after every clear until the score stands still, so that the trace
     * tells exactly what each piece earned. It makes the bot much slower, and what a clear
     * earns is known by now, so it is off unless asked for. Without it the bot still waits for
     * the few clears whose worth is not known for sure.
     */
    var measure: Boolean
        get() = preferences.getBoolean(KEY_MEASURE, false)
        set(value) = preferences.edit { putBoolean(KEY_MEASURE, value) }

    /**
     * What the bot does when the offered pieces no longer all fit. Switched off, it stops and
     * leaves the last pieces to the user. Switched on, it places what still fits, which ends
     * the game, and goes on with the next game once there is one.
     */
    var endless: Boolean
        get() = preferences.getBoolean(KEY_ENDLESS, false)
        set(value) = preferences.edit { putBoolean(KEY_ENDLESS, value) }

    /** The combo as the last run counted it, to go on with after the bot was started anew. */
    var savedCombo: SavedCombo?
        get() {
            val parts = preferences.getString(KEY_COMBO, null)?.split(",") ?: return null
            if (parts.size !in 3..4) return null
            val length = parts[0].toIntOrNull() ?: return null
            return SavedCombo(length, parts[1] == "true", parts[2].toLongOrNull() ?: return null, parts.getOrNull(3)?.toIntOrNull() ?: length)
        }
        set(value) = preferences.edit { putString(KEY_COMBO, value?.let { "${it.length},${it.clearedThisRound},${it.score},${it.longest}" }) }

    private companion object {
        const val KEY_COMBO = "combo"
        const val KEY_ENDLESS = "endless"
        const val KEY_MEASURE = "measure"
        const val KEY_STRATEGY = "strategy"
        const val KEY_KNOWN_BOARD = "known_board"
    }
}
