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

    /**
     * Adds a run. [reason] is `no_room` when the pieces did not fit any more, which is the end
     * of a game, or `stopped` when the run was ended from outside. [moves] and [lines] count
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
            file.parentFile?.mkdirs()
            if (!file.exists()) {
                file.writeText("ended,strategy,reason,moves,lines,seconds,score_start,score_end,screenshot,trace,version\n")
            }
            val ended = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val fields = listOf(
                ended, strategy.name.lowercase(), reason, moves, lines, seconds,
                scoreStart ?: "", scoreEnd ?: "", screenshot, trace, BuildConfig.VERSION_NAME,
            )
            file.appendText(fields.joinToString(",") + "\n")
        }
    }
}
