package io.github.andreasscholz.bbbot.bot

import android.content.Context
import androidx.core.content.edit
import io.github.andreasscholz.bbbot.game.Strategy
import io.github.andreasscholz.bbbot.vision.KnownBoard

/** What the user chose in the app, kept across restarts. */
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
     * tells exactly what each piece earned. It makes the bot much slower. Without it the bot
     * still waits for the few clears whose worth is not known yet, and once when it was
     * started into a running combo, to find out how long that is.
     */
    var measure: Boolean
        get() = preferences.getBoolean(KEY_MEASURE, false)
        set(value) = preferences.edit { putBoolean(KEY_MEASURE, value) }

    private companion object {
        const val KEY_MEASURE = "measure"
        const val KEY_STRATEGY = "strategy"
        const val KEY_KNOWN_BOARD = "known_board"
    }
}
