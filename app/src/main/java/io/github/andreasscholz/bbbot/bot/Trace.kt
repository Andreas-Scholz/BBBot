package io.github.andreasscholz.bbbot.bot

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import io.github.andreasscholz.bbbot.vision.ComboIcon
import io.github.andreasscholz.bbbot.vision.Screenshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * A record of one run, move by move: `captures/trace-<start>/moves.csv` with a line per piece
 * placed, and next to it a small picture of the score as it was before that piece. It exists to
 * find out how the game counts combos and points, which it does not tell: the lines say what
 * the bot did, the pictures show what the game made of it.
 */
class Trace(context: Context) {

    private val root = File(context.getExternalFilesDir(null), "captures")
    private val directory = File(root, "trace-${LocalDateTime.now().format(START_TIME)}")
    private val moves = File(directory, "moves.csv")

    /**
     * Adds the piece about to be placed. [shot] is the screen before it, [move] its number in
     * the run, [tray] the number of pieces on offer, [cells] its blocks, [lines] the lines it is
     * expected to clear, [clearPossible] whether the pieces on offer could clear anything,
     * [comboLength] and [clearedThisRound] the combo as the bot counts it and
     * [previousAsExpected] whether the piece before this one ended up where it was meant to.
     */
    suspend fun add(
        shot: Screenshot,
        move: Int,
        seconds: Long,
        tray: Int,
        cells: Int,
        lines: Int,
        clearPossible: Boolean,
        comboLength: Int,
        clearedThisRound: Boolean,
        previousAsExpected: Boolean,
    ) = withContext(Dispatchers.IO) {
        if (!directory.exists()) {
            directory.mkdirs()
            moves.writeText("move,seconds,tray,cells,lines,clear_possible,combo_length,cleared_this_round,previous_as_expected,icon,picture\n")
            prune()
        }
        val picture = "%05d.jpg".format(move)
        val icon = "%.3f".format(java.util.Locale.ROOT, ComboIcon.share(shot))
        moves.appendText("$move,$seconds,$tray,$cells,$lines,$clearPossible,$comboLength,$clearedThisRound,$previousAsExpected,$icon,$picture\n")
        savePicture(shot, File(directory, picture))
    }

    /** The strip of the screen with the score, at half size. */
    private fun savePicture(shot: Screenshot, file: File) {
        val top = (shot.height * PICTURE_TOP).toInt()
        val height = (shot.height * PICTURE_BOTTOM).toInt() - top
        val strip = Bitmap.createBitmap(shot.pixels, top * shot.width, shot.width, shot.width, height, Bitmap.Config.ARGB_8888)
        val small = strip.scale(shot.width / 2, height / 2)
        file.outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        strip.recycle()
        small.recycle()
    }

    private fun prune() {
        root.listFiles { file -> file.isDirectory && file.name.startsWith("trace-") }.orEmpty()
            .sortedByDescending { it.name }.drop(MAX_TRACES).forEach { it.deleteRecursively() }
    }

    private companion object {
        const val MAX_TRACES = 12
        const val PICTURE_TOP = 0.10f
        const val PICTURE_BOTTOM = 0.235f
        val START_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
