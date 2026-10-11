package io.github.andreasscholz.bbbot.bot

import android.os.SystemClock
import android.util.Log
import io.github.andreasscholz.bbbot.game.Board
import io.github.andreasscholz.bbbot.game.Combo
import io.github.andreasscholz.bbbot.game.HeuristicEvaluator
import io.github.andreasscholz.bbbot.game.Solver
import io.github.andreasscholz.bbbot.vision.ComboIcon
import io.github.andreasscholz.bbbot.vision.GameScreen
import io.github.andreasscholz.bbbot.vision.ScreenReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** What the bot is doing, for the panel. */
data class PlayStatus(val state: State, val moves: Int, val lines: Int) {
    enum class State {
        /** Pieces are being placed. */
        PLAYING,

        /** The screen does not show a game waiting for a move. */
        NO_GAME,

        /** The offered pieces do not all fit any more; the bot has stopped. */
        NO_MOVE,
    }
}

/**
 * Plays the game: look, think, move, and look again. Every turn starts from a fresh screenshot
 * and places a single piece, so a drag that went wrong or anything else unexpected on the
 * screen is simply seen and dealt with on the next turn.
 */
class Player(
    private val screenCapture: ScreenCapture,
    private val finger: Finger,
    private val captureStore: CaptureStore,
    private val solver: Solver,
    private val trace: Trace,
    private val settings: BotSettings,
    private val onStatus: (PlayStatus) -> Unit,
) {
    private var rejection = ""
    private val reader = ScreenReader(onReject = { rejection = it }).apply { known = settings.knownBoard }
    private var savedBoard = reader.known

    /**
     * Plays until the offered pieces do not all fit any more, or until the coroutine is
     * cancelled. In the first case the bot leaves the board as it is instead of placing what
     * still fits, so that the end of the game can be looked at.
     */
    suspend fun play() {
        var moves = 0
        var lines = 0
        var pending: PendingMove? = null
        var unreadable = 0
        var rejected = 0
        // The combo as the bot counts it. The game shows its length only in passing, so it is
        // counted here: one for every piece that clears something, and over when a round of three
        // pieces went by without a clear.
        var comboLength = -1
        var clearedThisRound = false
        var lastTray = 0
        // The board on which the pieces did not fit at the last look.
        var deadEnd: Board? = null
        var previousAsExpected = true
        val started = SystemClock.elapsedRealtime()
        try {
            while (true) {
                val shot = screenCapture.capture()
                val screen = withContext(Dispatchers.Default) { reader.read(shot) }

                if (screen == null || screen.tray.isEmpty()) {
                    // Also seen briefly in every game while lines vanish and new pieces arrive.
                    unreadable++
                    if (unreadable >= UNREADABLE_BEFORE_REPORT) onStatus(PlayStatus(PlayStatus.State.NO_GAME, moves, lines))
                    if (screen == null && unreadable == UNREADABLE_BEFORE_SAVE) {
                        val file = captureStore.save(shot, "unreadable")
                        Log.i(TAG, "Cannot read the screen after $moves moves ($rejection), saved ${file.name}")
                    }
                    delay(RETRY_MS)
                    continue
                }
                unreadable = 0
                if (reader.known != savedBoard) {
                    // Kept for the next run, which may start on a screen that hides the board.
                    savedBoard = reader.known
                    settings.knownBoard = savedBoard
                }

                val move = pending
                if (move != null) {
                    val settled = screen.board == move.expected || screen.board == move.seen || move.looks >= MAX_LOOKS
                    if (!settled) {
                        // Probably caught while lines vanish: wait until two looks in a row agree.
                        Log.d(TAG, "Board differs from the expected one:\n${screen.board}\nexpected:\n${move.expected}")
                        pending = move.copy(seen = screen.board, looks = move.looks + 1)
                        delay(RETRY_MS)
                        continue
                    }
                    // A piece that slipped back into the tray leaves the board as it was.
                    if (screen.board != move.before) {
                        moves++
                        rejected = 0
                        // Fewer blocks than the piece added means that lines were cleared.
                        val cleared = screen.board.filledCount < move.before.filledCount + move.cells
                        if (cleared) {
                            comboLength++
                            clearedThisRound = true
                        }
                    } else if (++rejected == REJECTED_BEFORE_SAVE) {
                        // The game keeps refusing the move, so the bot misreads something.
                        val file = captureStore.save(shot, "rejected")
                        Log.i(TAG, "The game refuses the move, saved ${file.name}")
                    }
                    if (screen.board == move.expected) lines += move.lines
                    previousAsExpected = screen.board == move.expected
                    pending = null
                }

                if (comboLength < 0) {
                    // Started in the middle of a game: the symbol behind the score tells whether a
                    // combo is running, but not for how long. It is taken to be a long one.
                    comboLength = if (ComboIcon.share(shot) > COMBO_CLEARLY_RUNNING) UNKNOWN_COMBO else 0
                    clearedThisRound = comboLength > 0
                }
                if (screen.tray.size == HeuristicEvaluator.ROUND && lastTray != HeuristicEvaluator.ROUND) {
                    // A new round begins. The one before it had to clear something to keep the combo.
                    if (!clearedThisRound) comboLength = 0
                    clearedThisRound = false
                }
                lastTray = screen.tray.size

                val plan = withContext(Dispatchers.Default) {
                    solver.solve(screen.board, screen.tray.map { it.piece }, Combo(comboLength, clearedThisRound))
                }
                if (!plan.complete) {
                    // Stopping ends the run, so a board misread in mid-animation must not cause it:
                    // the same verdict is needed on two looks in a row.
                    if (deadEnd != screen.board) {
                        deadEnd = screen.board
                        delay(RETRY_MS)
                        continue
                    }
                    Log.i(TAG, "Not all pieces fit after $moves moves:\n${screen.board}")
                    onStatus(PlayStatus(PlayStatus.State.NO_MOVE, moves, lines))
                    return
                }
                deadEnd = null
                val placement = plan.placements.first()

                onStatus(PlayStatus(PlayStatus.State.PLAYING, moves, lines))
                val result = screen.board.place(placement.piece, placement.row, placement.col)
                trace.add(
                    shot = shot,
                    move = moves,
                    seconds = (SystemClock.elapsedRealtime() - started) / 1000,
                    tray = screen.tray.size,
                    cells = placement.piece.cellCount,
                    lines = result.linesCleared,
                    clearPossible = plan.clearPossible,
                    comboLength = comboLength,
                    clearedThisRound = clearedThisRound,
                    previousAsExpected = previousAsExpected,
                )
                if (drag(screen, DragPlan.of(screen.geometry, screen.tray[placement.pieceIndex], placement))) {
                    pending = PendingMove(
                        before = screen.board,
                        expected = result.board,
                        lines = result.linesCleared,
                        cells = placement.piece.cellCount,
                    )
                    delay(if (result.linesCleared > 0) CLEAR_MS else SETTLE_MS)
                } else {
                    delay(RETRY_MS)
                }
            }
        } finally {
            withContext(NonCancellable) { finger.release() }
        }
    }

    /** Returns false when the gesture was interrupted, for example by a real finger on the screen. */
    private suspend fun drag(screen: GameScreen, plan: DragPlan): Boolean = try {
        finger.down(plan.fromX, plan.fromY, PRESS_MS)
        finger.moveTo(plan.toX, plan.toY, dragDuration(screen, plan))
        finger.up(HOLD_MS)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: GestureException) {
        Log.w(TAG, "Drag failed", e)
        finger.release()
        false
    }

    /** Longer ways take longer, so the piece never moves faster than the game can follow. */
    private fun dragDuration(screen: GameScreen, plan: DragPlan): Long {
        val cells = maxOf(abs(plan.toX - plan.fromX), abs(plan.toY - plan.fromY)) / screen.geometry.cellSize
        return (DRAG_BASE_MS + cells * DRAG_MS_PER_CELL).toLong()
    }

    /** A drag that was made and still has to show up on the screen. */
    private data class PendingMove(
        val before: Board,
        val expected: Board,
        val lines: Int,
        /** The number of blocks of the piece. */
        val cells: Int,
        /** The board of the latest look that did not show the expected one. */
        val seen: Board? = null,
        val looks: Int = 0,
    )

    private companion object {
        const val TAG = "BBBot"

        const val PRESS_MS = 60L
        const val DRAG_BASE_MS = 120L
        const val DRAG_MS_PER_CELL = 40f
        const val HOLD_MS = 120L

        /** Waiting times after a piece was dropped, without and with lines being cleared. */
        const val SETTLE_MS = 150L
        const val CLEAR_MS = 450L
        const val RETRY_MS = 300L

        /** Share of the combo symbol above which a combo is running beyond doubt, in any theme. */
        const val COMBO_CLEARLY_RUNNING = 0.1f

        /** The length taken for a combo that was running before the bot started. */
        const val UNKNOWN_COMBO = 1000

        const val MAX_LOOKS = 6
        const val REJECTED_BEFORE_SAVE = 3

        const val UNREADABLE_BEFORE_REPORT = 4
        const val UNREADABLE_BEFORE_SAVE = 8
    }
}
