package io.github.andreasscholz.bbbot.bot

import android.util.Log
import io.github.andreasscholz.bbbot.game.Board
import io.github.andreasscholz.bbbot.game.Solver
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

        /** None of the offered pieces fits; the game is over. */
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
    private val onStatus: (PlayStatus) -> Unit,
) {
    private val reader = ScreenReader()
    private val solver = Solver()

    /** Plays until the coroutine is cancelled. */
    suspend fun play() {
        var moves = 0
        var lines = 0
        var pending: PendingMove? = null
        var unreadable = 0
        try {
            while (true) {
                val shot = screenCapture.capture()
                val screen = withContext(Dispatchers.Default) { reader.read(shot) }

                if (screen == null || screen.tray.isEmpty()) {
                    // Also seen briefly in every game while lines vanish and new pieces arrive.
                    unreadable++
                    if (unreadable >= UNREADABLE_BEFORE_REPORT) onStatus(PlayStatus(PlayStatus.State.NO_GAME, moves, lines))
                    if (screen == null && unreadable == UNREADABLE_BEFORE_SAVE) captureStore.save(shot, "unreadable")
                    delay(RETRY_MS)
                    continue
                }
                unreadable = 0

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
                    if (screen.board != move.before) moves++
                    if (screen.board == move.expected) lines += move.lines
                    pending = null
                }

                val plan = withContext(Dispatchers.Default) { solver.solve(screen.board, screen.tray.map { it.piece }) }
                val placement = plan.placements.firstOrNull()
                if (placement == null) {
                    onStatus(PlayStatus(PlayStatus.State.NO_MOVE, moves, lines))
                    delay(RETRY_MS)
                    continue
                }

                onStatus(PlayStatus(PlayStatus.State.PLAYING, moves, lines))
                val result = screen.board.place(placement.piece, placement.row, placement.col)
                if (drag(screen, DragPlan.of(screen.geometry, screen.tray[placement.pieceIndex], placement))) {
                    pending = PendingMove(screen.board, result.board, result.linesCleared)
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

        const val MAX_LOOKS = 6

        const val UNREADABLE_BEFORE_REPORT = 4
        const val UNREADABLE_BEFORE_SAVE = 8
    }
}
