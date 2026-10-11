package io.github.andreasscholz.bbbot.bot

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import io.github.andreasscholz.bbbot.game.Board
import io.github.andreasscholz.bbbot.game.Placement
import io.github.andreasscholz.bbbot.vision.ComboIcon
import io.github.andreasscholz.bbbot.vision.Screenshot
import io.github.andreasscholz.bbbot.vision.TrayPiece
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What the bot saw and did at one piece, for the trace. Everything is as it was before the
 * piece was placed, except what is said to be expected of it.
 */
class TraceStep(
    /** The number of the piece in the run, from 0. */
    val move: Int,
    val seconds: Long,
    /** The number of the round of three pieces in the run, from 1. */
    val round: Int,
    val board: Board,
    val offered: List<TrayPiece>,
    /** The piece that is placed and where; null in the last line of a run, where nothing is placed. */
    val placement: Placement?,
    val slot: Int,
    /** The lines the piece is expected to clear, and the board expected after it. */
    val lines: Int,
    val boardAfter: Board,
    /** Whether the offered pieces could clear a line at all. */
    val clearPossible: Boolean,
    /** The combo as the bot counts it. */
    val comboLength: Int,
    val clearedThisRound: Boolean,
    /** Whether the piece before this one ended up where it was meant to. */
    val previousAsExpected: Boolean,
    /** The score on the screen, if it could be read, and whether the look before showed the same. */
    val score: Long?,
    val scoreStable: Boolean,
)

/**
 * A record of one run, piece by piece: `captures/trace-<start>/moves.csv` with a line per piece
 * placed, and next to it a small picture of the score as it was before that piece. It exists to
 * find out what the game pays for: the difference between the scores of two lines is what the
 * piece of the first line earned, and the line says what that piece did. Boards and pieces are
 * written as hexadecimal numbers with one bit per cell, the top left cell being the lowest.
 */
class Trace(context: Context) {

    private val root = File(context.getExternalFilesDir(null), "captures")
    private val directory = File(root, "trace-${LocalDateTime.now().format(START_TIME)}")
    private val moves = File(directory, "moves.csv")

    /** The name of the folder of this trace. */
    val name: String get() = directory.name

    suspend fun add(shot: Screenshot, step: TraceStep) = withContext(Dispatchers.IO) {
        if (!directory.exists()) {
            directory.mkdirs()
            moves.writeText(HEADER + "\n")
            prune()
        }
        val placement = step.placement
        val picture = if (placement != null) "%05d.jpg".format(step.move) else "end.jpg"
        val fields = listOf(
            step.move,
            step.seconds,
            step.round,
            hex(step.board.bits),
            step.offered.joinToString(";") { "${it.slot}:${hex(it.piece.mask)}" },
            if (placement != null) step.slot else "",
            placement?.let { hex(it.piece.mask) } ?: "",
            placement?.row ?: "",
            placement?.col ?: "",
            placement?.piece?.cellCount ?: "",
            if (placement != null) step.lines else "",
            if (placement != null) step.boardAfter == Board.EMPTY else "",
            step.clearPossible,
            step.comboLength,
            step.clearedThisRound,
            step.previousAsExpected,
            "%.3f".format(Locale.ROOT, ComboIcon.share(shot)),
            step.score ?: "",
            step.scoreStable,
            picture,
        )
        moves.appendText(fields.joinToString(",") + "\n")
        savePicture(shot, File(directory, picture))
    }

    private fun hex(bits: Long): String = java.lang.Long.toHexString(bits)

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
        const val HEADER = "move,seconds,round,board,offered,slot,piece,row,col,cells,lines,all_clear,clear_possible," +
            "combo_length,cleared_this_round,previous_as_expected,icon,score,score_stable,picture"
        const val MAX_TRACES = 30
        const val PICTURE_TOP = 0.10f
        const val PICTURE_BOTTOM = 0.235f
        val START_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
