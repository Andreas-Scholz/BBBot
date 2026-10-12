package io.github.andreasscholz.bbbot.bot

import android.os.SystemClock
import android.util.Log
import io.github.andreasscholz.bbbot.game.Board
import io.github.andreasscholz.bbbot.game.Combo
import io.github.andreasscholz.bbbot.game.HeuristicEvaluator
import io.github.andreasscholz.bbbot.game.Scoring
import io.github.andreasscholz.bbbot.game.Piece
import io.github.andreasscholz.bbbot.game.Solver
import io.github.andreasscholz.bbbot.vision.ComboIcon
import io.github.andreasscholz.bbbot.vision.GameScreen
import io.github.andreasscholz.bbbot.vision.MainMenu
import io.github.andreasscholz.bbbot.vision.PANEL_COVERS_GAME
import io.github.andreasscholz.bbbot.vision.ScoreReader
import io.github.andreasscholz.bbbot.vision.ScreenArea
import io.github.andreasscholz.bbbot.vision.ScreenReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** What the bot is doing, for the panel. */
/**
 * [combo] is the combo as the game shows it after a clear, and [longest] the longest it has
 * been in this game. [comboKnown] tells whether it was measured in this run or is still what
 * the bot took over or assumed.
 */
data class PlayStatus(
    val state: State,
    val moves: Int,
    val lines: Int,
    val score: Long? = null,
    val combo: Int = 0,
    val longest: Int = 0,
    val comboKnown: Boolean = false,
) {
    enum class State {
        /** Pieces are being placed. */
        PLAYING,

        /** The screen does not show a game waiting for a move. */
        NO_GAME,

        /** The offered pieces do not all fit any more; the bot has stopped. */
        NO_MOVE,

        /** The bot's own panel lies on the board or on the pieces. */
        PANEL_IN_THE_WAY,
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
    /** Whether the game is what the screen shows, as far as the service can tell. */
    private val gameInFront: () -> Boolean = { true },
    /** Called when a look showed the game beyond doubt. */
    private val onGameSeen: () -> Unit = {},
    /** Where the bot's panel lies on the screen, if it is shown. */
    private val panel: () -> ScreenArea? = { null },
) {
    /** Called in endless play when a game is over, before anything of what follows is looked at. */
    var onGameOver: suspend () -> Unit = {}

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
        // The longest the combo has been in this game.
        var longestCombo = 0
        // The game does not tell the length of a combo that is running when the bot joins, and
        // a count that starts from a guess stays wrong for as long as the combo lasts. But what
        // a clear earns tells it to the piece (see [Scoring.comboLength]). So the bot measures:
        // it lets the score settle after one clearing piece and again after the next, and reads
        // the length off the difference. It does so at the start of a run and then every now
        // and then, because each time costs a few seconds.
        var comboKnown = false
        var clearingPieces = 0
        var clearsAtCheck = 0
        // The score when it last stood still after a clear, and what was placed since.
        var settledScore: Long? = null
        var cellsSince = 0
        var clearsSince = 0
        var linesOfClear = 0
        var clearEmptied = false
        // What the panel shows of the combo: the number the game shows after a clear, which
        // does not count the piece that began the combo. Before the bot has seen the game, that
        // is the combo as the last run left it, so that it stays in sight when the screen
        // cannot be read.
        fun status(state: PlayStatus.State, moves: Int, lines: Int, score: Long? = null): PlayStatus {
            val saved = if (comboLength < 0) settings.savedCombo else null
            return PlayStatus(
                state, moves, lines, score,
                combo = maxOf((saved?.length ?: comboLength) - 1, 0),
                longest = maxOf((saved?.longest ?: longestCombo) - 1, 0),
                comboKnown = comboKnown,
            )
        }
        var clearedThisRound = false
        var lastTray = 0
        // The pieces of a round that is beginning, as the look before showed them.
        var arriving: List<Piece>? = null
        // The board on which the pieces did not fit at the last look.
        var deadEnd: Board? = null
        var previousAsExpected = true
        val started = SystemClock.elapsedRealtime()
        var round = 0
        // The score as the last look showed it, and whether a piece that cleared something has
        // been placed since the score was last seen standing still.
        var lastScore: Long? = null
        val scoreFilter = ScoreFilter()
        var scoreAtMove: Long? = null
        var equalLooks = 0
        var placedAt = 0L
        var scoreMoving = false
        var scoreLooks = 0
        val measure = settings.measure
        val endless = settings.endless
        // In endless play: whether the game is over and the next one is waited for, and how
        // many pictures of what the game shows meanwhile have been kept.
        var over = false
        var picturesAfter = 0
        // Whether the score is waited for after the piece placed last.
        var exact = false
        try {
            while (true) {
                val shot = screenCapture.capture()
                // The panel lies on top of the game and is in every picture of the screen.
                val covered = panel()
                reader.panel = covered
                if (covered != null) shot.paintOver(covered)
                rejection = ""
                val screen = withContext(Dispatchers.Default) { reader.read(shot) }

                if (screen == null || screen.tray.isEmpty()) {
                    // Also seen briefly in every game while lines vanish and new pieces arrive.
                    unreadable++
                    if (rejection == PANEL_COVERS_GAME) {
                        onStatus(status(PlayStatus.State.PANEL_IN_THE_WAY, moves, lines))
                    } else if (unreadable >= UNREADABLE_BEFORE_REPORT) {
                        onStatus(status(PlayStatus.State.NO_GAME, moves, lines))
                    }
                    // With the notification shade or anything else of the system on top, the
                    // screen is none of the bot's business: nothing is kept of it or tapped on it.
                    if (screen == null && gameInFront()) {
                        val classic = MainMenu.classicButton(shot)
                        if (classic != null && unreadable >= MENU_LOOKS) {
                            // The game was started anew and waits in its main menu.
                            Log.i(TAG, "The main menu: going on with the game")
                            finger.down(classic.first.toFloat(), classic.second.toFloat(), TAP_MS)
                            finger.up(durationMs = 1)
                            unreadable = 0
                            delay(MENU_MS)
                            continue
                        }
                        if (over && picturesAfter < PICTURES_AFTER && unreadable % LOOKS_PER_PICTURE == 0) {
                            // What the game shows between two games is not known yet.
                            captureStore.save(shot, "after")
                            picturesAfter++
                        } else if (unreadable == UNREADABLE_BEFORE_SAVE) {
                            val file = captureStore.save(shot, "unreadable")
                            Log.i(TAG, "Cannot read the screen after $moves moves ($rejection), saved ${file.name}")
                        }
                    }
                    delay(RETRY_MS)
                    continue
                }
                unreadable = 0
                onGameSeen()
                if (over && screen.board != deadEnd) {
                    Log.i(TAG, "A new game after $moves moves")
                    over = false
                    deadEnd = null
                    pending = null
                    comboLength = 0
                    longestCombo = 0
                    clearedThisRound = false
                    lastTray = 0
                }
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
                        cellsSince += move.cells
                        // Fewer blocks than the piece added means that lines were cleared.
                        val cleared = screen.board.filledCount < move.before.filledCount + move.cells
                        if (cleared) {
                            comboLength++
                            clearedThisRound = true
                            clearingPieces++
                            // A clear that did not go as planned cannot be measured.
                            clearsSince += if (screen.board == move.expected) 1 else 2
                            linesOfClear = move.lines
                            clearEmptied = move.expected == Board.EMPTY
                        }
                    } else if (++rejected == REJECTED_BEFORE_SAVE) {
                        // The game keeps refusing the move, so the bot misreads something.
                        val file = captureStore.save(shot, "rejected")
                        Log.i(TAG, "The game refuses the move, saved ${file.name}")
                    }
                    if (screen.board == move.expected) lines += move.lines
                    previousAsExpected = screen.board == move.expected
                    if (move.lines > 0) scoreMoving = true
                    exact = move.exact
                    pending = null
                }

                // The game counts the score up over a moment after a clear. To know what a piece
                // earned, the next one waits until two looks in a row show the same score.
                val score = scoreFilter.accept(ScoreReader.read(shot))
                val scoreStable = score != null && score == lastScore
                equalLooks = if (scoreStable) equalLooks + 1 else 0
                lastScore = score
                // The score counts up late and in stages: first the points for the blocks, then,
                // after a pause, those for the clear. So it only counts as settled when it has
                // gone up since the piece was placed, has stood still for three looks in a row,
                // and the clear is a while ago.
                val counted = equalLooks >= 2 && (scoreAtMove == null || score!! > scoreAtMove) &&
                    SystemClock.elapsedRealtime() - placedAt >= SETTLE_SCORE_MS
                if ((measure || exact) && scoreMoving && !counted && scoreLooks < MAX_SCORE_LOOKS) {
                    scoreLooks++
                    delay(SCORE_MS)
                    continue
                }
                if (scoreMoving) {
                    val before = settledScore
                    settledScore = score?.takeIf { counted && (measure || exact) }
                    val now = settledScore
                    if (before != null && now != null && clearsSince == 1 && comboLength >= 0) {
                        val measured = Scoring.comboLength(now - before - cellsSince, linesOfClear, clearEmptied)
                        if (measured != null) {
                            if (measured != comboLength) {
                                Log.i(TAG, "The combo is $measured long, not $comboLength")
                                // What was counted on top of a wrong start is off by as much.
                                longestCombo = maxOf(measured, longestCombo + measured - comboLength)
                                comboLength = measured
                            }
                            comboKnown = true
                            clearsAtCheck = clearingPieces
                        }
                    }
                    cellsSince = 0
                    clearsSince = 0
                }
                exact = false
                scoreMoving = false
                scoreLooks = 0
                scoreAtMove = score

                // New pieces grow into place, and a look in the middle of that reads them wrong: in
                // traces one round in thirteen began with such a reading, and one of them cost a
                // combo of a thousand. So a round only begins once two looks show the same pieces.
                if (screen.tray.size == HeuristicEvaluator.ROUND && lastTray != HeuristicEvaluator.ROUND) {
                    val pieces = screen.tray.map { it.piece }
                    if (pieces != arriving) {
                        arriving = pieces
                        delay(ARRIVING_MS)
                        continue
                    }
                }
                arriving = null

                if (comboLength < 0) {
                    // A run that goes on where the last one stopped, after a new version was
                    // installed for example, takes over the combo as that run counted it.
                    settings.savedCombo?.let { saved ->
                        if (score != null && score >= saved.score && score - saved.score <= ScoreFilter.MAX_STEP) {
                            comboLength = saved.length
                            clearedThisRound = saved.clearedThisRound
                            longestCombo = saved.longest
                        }
                    }
                }
                if (comboLength < 0) {
                    // Started in the middle of a game: the symbol behind the score tells whether a
                    // combo is running, but not for how long. It is taken to be of middling length:
                    // taken for a very long one when it was not, it once had the board filled up
                    // to defend it until no round fit any more. Working the length out from what a
                    // clear earns was tried and gave nonsense, because the score counts up late
                    // and in stages, and there is no telling when it has taken a clear in.
                    comboLength = if (ComboIcon.share(shot) > COMBO_CLEARLY_RUNNING) UNKNOWN_COMBO else 0
                    clearedThisRound = comboLength > 0
                }
                if (screen.tray.size == HeuristicEvaluator.ROUND && lastTray != HeuristicEvaluator.ROUND) {
                    // A new round begins. The one before it had to clear something to keep the combo.
                    if (!clearedThisRound) comboLength = 0
                    clearedThisRound = false
                    round++
                }
                lastTray = screen.tray.size

                longestCombo = maxOf(longestCombo, comboLength)
                if (score != null) settings.savedCombo = SavedCombo(comboLength, clearedThisRound, score, longestCombo)
                val solveStarted = SystemClock.elapsedRealtime()
                val plan = withContext(Dispatchers.Default) {
                    solver.solve(screen.board, screen.tray.map { it.piece }, Combo(comboLength, clearedThisRound))
                }
                val solveMillis = SystemClock.elapsedRealtime() - solveStarted
                if (!plan.complete) {
                    // Stopping ends the run, so a board misread in mid-animation must not cause it:
                    // the same verdict is needed on two looks in a row.
                    if (deadEnd != screen.board) {
                        deadEnd = screen.board
                        delay(RETRY_MS)
                        continue
                    }
                    // In endless play the pieces that still fit are placed like any other.
                    val stuck = !endless || plan.placements.isEmpty()
                    if (stuck && !over) {
                    Log.i(TAG, "Not all pieces fit after $moves moves:\n${screen.board}")
                    trace.add(
                        shot,
                        TraceStep(
                            move = moves,
                            seconds = (SystemClock.elapsedRealtime() - started) / 1000,
                            round = round,
                            board = screen.board,
                            offered = screen.tray,
                            placement = null,
                            slot = -1,
                            lines = 0,
                            boardAfter = screen.board,
                            clearPossible = plan.clearPossible,
                            comboLength = comboLength,
                            clearedThisRound = clearedThisRound,
                            previousAsExpected = previousAsExpected,
                            score = score,
                            scoreStable = scoreStable,
                            solveMillis = solveMillis,
                        ),
                    )
                    onStatus(status(PlayStatus.State.NO_MOVE, moves, lines, score))
                    }
                    if (stuck) {
                        if (!endless) return
                        // Nothing fits any more, so the game is over. It is left to show what
                        // it shows; the bot goes on when it sees a game again.
                        if (!over) {
                            over = true
                            picturesAfter = 0
                            onGameOver()
                        }
                        delay(OVER_MS)
                        continue
                    }
                } else {
                    deadEnd = null
                }
                val placement = plan.placements.first()

                val result = screen.board.place(placement.piece, placement.row, placement.col)
                // What four and more lines at once earn was seen too rarely to be sure of, so
                // those are always waited for; the trace then tells.
                val measuring = !comboKnown || clearingPieces - clearsAtCheck >= COMBO_CHECK_EVERY
                val waitForScore = measure || result.linesCleared >= 4 || result.linesCleared > 0 && measuring

                onStatus(status(PlayStatus.State.PLAYING, moves, lines, score))
                trace.add(
                    shot,
                    TraceStep(
                        move = moves,
                        seconds = (SystemClock.elapsedRealtime() - started) / 1000,
                        round = round,
                        board = screen.board,
                        offered = screen.tray,
                        placement = placement,
                        slot = screen.tray[placement.pieceIndex].slot,
                        lines = result.linesCleared,
                        boardAfter = result.board,
                        clearPossible = plan.clearPossible,
                        comboLength = comboLength,
                        clearedThisRound = clearedThisRound,
                        previousAsExpected = previousAsExpected,
                        score = score,
                        scoreStable = scoreStable,
                        solveMillis = solveMillis,
                    ),
                )
                if (drag(screen, DragPlan.of(screen.geometry, screen.tray[placement.pieceIndex], placement))) {
                    pending = PendingMove(
                        before = screen.board,
                        expected = result.board,
                        lines = result.linesCleared,
                        cells = placement.piece.cellCount,
                        exact = waitForScore,
                    )
                    placedAt = SystemClock.elapsedRealtime()
                    if (result.linesCleared > 0 && score != null) {
                        // Kept as it will be once the piece has landed: a run that is ended
                        // right now, by a new version for example, would otherwise leave behind
                        // a round without a clear, and the next run would take the combo for lost.
                        settings.savedCombo = SavedCombo(comboLength + 1, clearedThisRound = true, score, maxOf(longestCombo, comboLength + 1))
                    }
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
        /** Whether the next piece waits until the score has taken in what this one earned. */
        val exact: Boolean,
        /** The board of the latest look that did not show the expected one. */
        val seen: Board? = null,
        val looks: Int = 0,
    )

    private companion object {
        const val TAG = "BBBot"

        // How long a drag takes. Not to be shortened: with half these times three pieces in 130
        // landed beside their place, and one of them cost a combo of 641.
        const val PRESS_MS = 60L
        const val DRAG_BASE_MS = 120L
        const val DRAG_MS_PER_CELL = 40f
        const val HOLD_MS = 120L

        /**
         * Waiting times after a piece was dropped, without and with lines being cleared, and
         * before another look when the screen was not ready. Looking too early costs nothing
         * but another look, so these are as short as still saves looks.
         */
        const val SETTLE_MS = 100L
        const val CLEAR_MS = 350L
        const val RETRY_MS = 150L

        /** Share of the combo symbol above which a combo is running beyond doubt, in any theme. */
        const val COMBO_CLEARLY_RUNNING = 0.1f

        /** The length taken for a combo that was running before the bot started. */
        const val UNKNOWN_COMBO = 100

        const val MAX_LOOKS = 6
        const val ARRIVING_MS = 50L

        // Waiting for the score to stand still after a clear: how often to look, and how long between.
        const val SETTLE_SCORE_MS = 3000L
        const val MAX_SCORE_LOOKS = 30
        const val SCORE_MS = 100L
        const val REJECTED_BEFORE_SAVE = 3

        const val UNREADABLE_BEFORE_REPORT = 4
        const val UNREADABLE_BEFORE_SAVE = 8

        // The main menu has to be there on this many looks in a row before it is tapped, and
        // the game is given this long to come up after the tap.
        // In endless play after a game is over: how often to look, and how many pictures to keep
        // of what the game shows, one in so many looks at a screen that is not the game.
        const val OVER_MS = 1000L
        const val PICTURES_AFTER = 40
        const val LOOKS_PER_PICTURE = 10

        /** After how many clearing pieces the length of the combo is measured again. */
        const val COMBO_CHECK_EVERY = 250

        const val MENU_LOOKS = 3
        const val TAP_MS = 60L
        const val MENU_MS = 3000L
    }
}
