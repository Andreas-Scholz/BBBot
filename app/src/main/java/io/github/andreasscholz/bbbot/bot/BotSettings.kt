package io.github.andreasscholz.bbbot.bot

import android.content.Context
import androidx.core.content.edit
import io.github.andreasscholz.bbbot.game.Strategy

/** What the user chose in the app, kept across restarts. */
class BotSettings(context: Context) {

    private val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** How the bot plays. Read when the bot is started, so a change applies to the next start. */
    var strategy: Strategy
        get() = preferences.getString(KEY_STRATEGY, null)
            ?.let { name -> Strategy.entries.find { it.name == name } }
            ?: Strategy.BALANCED
        set(value) = preferences.edit { putString(KEY_STRATEGY, value.name) }

    private companion object {
        const val KEY_STRATEGY = "strategy"
    }
}
