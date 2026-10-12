package io.github.andreasscholz.bbbot.bot

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import io.github.andreasscholz.bbbot.R
import io.github.andreasscholz.bbbot.game.HeuristicEvaluator
import io.github.andreasscholz.bbbot.game.Solver
import io.github.andreasscholz.bbbot.game.Strategy
import io.github.andreasscholz.bbbot.ui.label
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The bot's hands and eyes. Android only lets an accessibility service take screenshots of
 * other apps and touch the screen for the user. The service does not read window contents;
 * it only listens for which app comes to the front, to show its panel on top of the game.
 */
class BotService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var screenCapture: ScreenCapture
    private lateinit var captureStore: CaptureStore
    private lateinit var overlay: BotOverlay
    private lateinit var settings: BotSettings
    private lateinit var gameLog: GameLog
    private var strategy = Strategy.BALANCED

    // The first and the last score read off the screen in the current run.
    private var firstScore: Long? = null
    private var lastScore: Long? = null
    private var playing: Job? = null

    /**
     * Whether something of the system may lie on top of the game. While it may, no picture of
     * the screen is kept and nothing is tapped. It is over when the game is seen again.
     */
    @Volatile
    private var covered = false
    private var lastStatus = PlayStatus(PlayStatus.State.PLAYING, moves = 0, lines = 0)

    override fun onServiceConnected() {
        screenCapture = ScreenCapture(this)
        captureStore = CaptureStore(this)
        overlay = BotOverlay(this, onToggle = ::togglePlaying, onCapture = ::saveScreenshotOnRequest)
        overlay.setStatus(getString(R.string.overlay_ready))
        settings = BotSettings(this)
        gameLog = GameLog(this)
        mutableConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        when (event.packageName?.toString()) {
            GAME_PACKAGE -> {
                covered = false
                overlay.show()
            }
            // The notification shade, the lock screen and the like. They lie on top of the game
            // without it going away, and nothing tells when they are gone again.
            SYSTEM_UI_PACKAGE -> covered = true
            // The panel itself also reports in.
            null, packageName -> Unit
            else -> {
                // Never touch the screen of anything but the game.
                stopPlaying()
                overlay.hide()
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        mutableConnected.value = false
        overlay.hide()
        scope.cancel()
        return super.onUnbind(intent)
    }

    private fun togglePlaying() {
        if (playing?.isActive == true) stopPlaying() else startPlaying()
    }

    private fun startPlaying() {
        strategy = settings.strategy
        val solver = Solver(HeuristicEvaluator(strategy.weights))
        val trace = Trace(this)
        val player = Player(screenCapture, Finger(this), captureStore, solver, trace, settings, onStatus = ::showStatus, gameInFront = { !covered }, onGameSeen = { covered = false })
        firstScore = null
        lastStatus = PlayStatus(PlayStatus.State.PLAYING, moves = 0, lines = 0)
        showStatus(lastStatus)
        overlay.setPlaying(true)
        val played = strategy
        val started = SystemClock.elapsedRealtime()
        suspend fun log(reason: String, screenshot: String) = gameLog.add(
            strategy = played,
            reason = reason,
            moves = lastStatus.moves,
            lines = lastStatus.lines,
            seconds = (SystemClock.elapsedRealtime() - started) / 1000,
            scoreStart = firstScore,
            scoreEnd = lastScore,
            screenshot = screenshot,
            trace = trace.name,
        )
        // In endless play the run goes on after a game is over, so the game is put on record here.
        player.onGameOver = {
            val file = saveScreenshot("${CaptureStore.FINAL_PREFIX}-${played.name.lowercase()}")
            log("no_room", file.name)
        }
        playing = scope.launch {
            try {
                gameLog.recover()
                // A run that is cut short leaves no chance to put it on record, so it is noted
                // as it goes.
                val noting = launch {
                    while (true) {
                        delay(NOTE_MS)
                        gameLog.keep(played, lastStatus.moves, lastStatus.lines, (SystemClock.elapsedRealtime() - started) / 1000, firstScore, lastScore, trace.name)
                    }
                }
                try {
                    player.play()
                } finally {
                    noting.cancel()
                }
                // The pieces no longer fit: keep a picture of how the game ends, named after the
                // strategy that played it.
                val file = saveScreenshot("${CaptureStore.FINAL_PREFIX}-${played.name.lowercase()}")
                log("no_room", file.name)
                overlay.setStatus(getString(R.string.overlay_no_move, lastStatus.moves))
                // What the game shows when it is over is not known yet. A few pictures of what
                // follows are kept, to teach the bot to start a new game by itself one day.
                repeat(AFTER_PICTURES) {
                    delay(AFTER_MS)
                    if (!covered) saveScreenshot("after")
                }
            } catch (e: CancellationException) {
                overlay.setStatus(getString(R.string.overlay_stopped, lastStatus.moves, lastStatus.lines))
                withContext(NonCancellable) { log("stopped", "") }
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Playing failed", e)
                overlay.setStatus(getString(R.string.overlay_error))
            } finally {
                overlay.setPlaying(false)
            }
        }
    }

    private fun stopPlaying() {
        playing?.cancel()
        playing = null
    }

    private fun showStatus(status: PlayStatus) {
        lastStatus = status
        if (status.score != null) {
            if (firstScore == null) firstScore = status.score
            lastScore = status.score
        }
        val text = when (status.state) {
            PlayStatus.State.PLAYING ->
                getString(R.string.overlay_playing, status.moves, status.lines, getString(strategy.label))
            PlayStatus.State.NO_GAME -> getString(R.string.overlay_no_game, status.moves)
            PlayStatus.State.NO_MOVE -> getString(R.string.overlay_no_move, status.moves)
        }
        overlay.setStatus(text)
    }

    /** Saves what the bot sees as a PNG, for working on the recognition and for reporting problems. */
    private fun saveScreenshotOnRequest() {
        scope.launch {
            try {
                val file = saveScreenshot("capture")
                overlay.setStatus(getString(R.string.overlay_saved, file.name))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Saving a screenshot failed", e)
                overlay.setStatus(getString(R.string.overlay_capture_failed))
            }
        }
    }

    /** Saves a screenshot without the panel, so the picture shows the whole game and its score. */
    private suspend fun saveScreenshot(prefix: String): File {
        overlay.hide()
        try {
            delay(PANEL_HIDE_MS)
            return captureStore.save(screenCapture.capture(), prefix)
        } finally {
            overlay.show()
        }
    }

    companion object {
        const val GAME_PACKAGE = "com.block.juggle"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val TAG = "BBBot"
        private const val PANEL_HIDE_MS = 150L
        private const val AFTER_PICTURES = 20
        private const val AFTER_MS = 5000L
        private const val NOTE_MS = 20_000L

        private val mutableConnected = MutableStateFlow(false)

        /** Whether the service is switched on in the system settings and running. */
        val connected: StateFlow<Boolean> = mutableConnected
    }
}
