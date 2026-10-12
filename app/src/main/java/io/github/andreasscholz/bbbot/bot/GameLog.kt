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
 * A list of the bot's runs, one line each, next to the screenshots (`runs.csv`). It tells which
 * strategy played, how far it got and where the trace and the final screenshot of the run are,
 * so that the strategies can be compared by the points they really reach.
 */
class GameLog(context: Context) {

    private val file = File(File(context.getExternalFilesDir(null), "captures"), "runs.csv")

    /** The run that is going on, as the line it gets if it never comes to an end of its own. */
    private val open = File(file.parentFile, "run-open.csv")

    /**
     * Adds a run. [reason] is `no_room` when the pieces did not fit any more, which is the end
     * of a game, `stopped` when the run was ended from outside, or `interrupted` when the bot
     * itself was ended, by a new version being installed for example. [moves] and [lines] count
     * from the start of the run, which need not be the start of the game; [scoreStart] and
     * [scoreEnd] are the first and the last score read off the screen.
     */
    suspend fun add(
        strategy: Strategy,
        reason: String,
        moves: Int,
        lines: Int,
        seconds: Long,
        scoreStart: Long?,
        scoreEnd: Long?,
        screenshot: String,
        trace: String,
    ) {
        withContext(Dispatchers.IO) {
            append(line(strategy, reason, moves, lines, seconds, scoreStart, scoreEnd, screenshot, trace))
            open.delete()
        }
    }

    /** Notes how far the run that is going on has got, in case it is cut short. */
    suspend fun keep(strategy: Strategy, moves: Int, lines: Int, seconds: Long, scoreStart: Long?, scoreEnd: Long?, trace: String) {
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            open.writeText(line(strategy, "interrupted", moves, lines, seconds, scoreStart, scoreEnd, "", trace))
        }
    }

    /** Adds the run that was going on when the bot was last ended, if there was one. */
    suspend fun recover() {
        withContext(Dispatchers.IO) {
            if (open.exists()) {
                append(open.readText())
                open.delete()
            }
        }
    }

    private fun append(line: String) {
        file.parentFile?.mkdirs()
        if (!file.exists()) {
            file.writeText("ended,strategy,reason,moves,lines,seconds,score_start,score_end,screenshot,trace,version\n")
        }
        file.appendText(line)
    }

    private fun line(
        strategy: Strategy,
        reason: String,
        moves: Int,
        lines: Int,
        seconds: Long,
        scoreStart: Long?,
        scoreEnd: Long?,
        screenshot: String,
        trace: String,
    ): String {
        val ended = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        val fields = listOf(
            ended, strategy.name.lowercase(), reason, moves, lines, seconds,
            scoreStart ?: "", scoreEnd ?: "", screenshot, trace, BuildConfig.VERSION_NAME,
        )
        return fields.joinToString(",") + "\n"
    }
}
