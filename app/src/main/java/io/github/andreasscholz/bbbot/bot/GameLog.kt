package io.github.andreasscholz.bbbot.bot

import android.content.Context
import io.github.andreasscholz.bbbot.BuildConfig
import io.github.andreasscholz.bbbot.game.Strategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * A list of the bot's runs, one line each, next to the screenshots (`games.csv`). It is what
 * tells afterwards which strategy a final screenshot belongs to, so that the strategies can be
 * compared by the points they really reach. The points themselves are only in the screenshots;
 * the bot does not read the score.
 */
class GameLog(context: Context) {

    private val file = File(File(context.getExternalFilesDir(null), "captures"), "games.csv")

    /**
     * Adds a run. [reason] is `no_room` when the pieces did not fit any more, which is the end
     * of a game, or `stopped` when the run was ended from outside. [moves] and [lines] count
     * from the start of the run, which need not be the start of the game.
     */
    suspend fun add(strategy: Strategy, reason: String, moves: Int, lines: Int, seconds: Long, screenshot: String) {
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            if (!file.exists()) file.writeText("ended,strategy,reason,moves,lines,seconds,screenshot,version\n")
            val ended = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            file.appendText(
                "$ended,${strategy.name.lowercase()},$reason,$moves,$lines,$seconds,$screenshot,${BuildConfig.VERSION_NAME}\n",
            )
        }
    }
}
