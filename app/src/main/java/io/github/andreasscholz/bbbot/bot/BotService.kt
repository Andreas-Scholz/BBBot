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
    private var playing: Job? = null
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
            GAME_PACKAGE -> overlay.show()
            // The panel itself and system windows such as the notification shade also report in.
            null, packageName, SYSTEM_UI_PACKAGE -> Unit
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
        val player = Player(screenCapture, Finger(this), captureStore, solver, Trace(this), settings, onStatus = ::showStatus)
        lastStatus = PlayStatus(PlayStatus.State.PLAYING, moves = 0, lines = 0)
        showStatus(lastStatus)
        overlay.setPlaying(true)
        val played = strategy
        val started = SystemClock.elapsedRealtime()
        suspend fun log(reason: String, screenshot: String) = gameLog.add(
            played, reason, lastStatus.moves, lastStatus.lines, (SystemClock.elapsedRealtime() - started) / 1000, screenshot,
        )
        playing = scope.launch {
            try {
                player.play()
                // The pieces no longer fit: keep a picture of how the game ends, named after the
                // strategy that played it.
                val file = saveScreenshot("${CaptureStore.FINAL_PREFIX}-${played.name.lowercase()}")
                log("no_room", file.name)
                overlay.setStatus(getString(R.string.overlay_no_move, lastStatus.moves))
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

        private val mutableConnected = MutableStateFlow(false)

        /** Whether the service is switched on in the system settings and running. */
        val connected: StateFlow<Boolean> = mutableConnected
    }
}
