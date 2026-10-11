package io.github.andreasscholz.bbbot.bot

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import io.github.andreasscholz.bbbot.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The bot's hands and eyes. Android only lets an accessibility service take screenshots of
 * other apps and touch the screen for the user. The service does not read window contents;
 * it only listens for which app comes to the front, to show its panel on top of the game.
 */
class BotService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var screenCapture: ScreenCapture
    private lateinit var overlay: BotOverlay

    override fun onServiceConnected() {
        screenCapture = ScreenCapture(this)
        overlay = BotOverlay(this, onCapture = ::saveScreenshot)
        overlay.setStatus(getString(R.string.overlay_ready))
        mutableConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        when (event.packageName?.toString()) {
            GAME_PACKAGE -> overlay.show()
            // The panel itself and system windows such as the notification shade also report in.
            null, packageName, SYSTEM_UI_PACKAGE -> Unit
            else -> overlay.hide()
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        mutableConnected.value = false
        overlay.hide()
        scope.cancel()
        return super.onUnbind(intent)
    }

    /** Saves what the bot sees as a PNG, for working on the recognition and for reporting problems. */
    private fun saveScreenshot() {
        scope.launch {
            try {
                // Without the panel, so the picture shows the whole game.
                overlay.hide()
                delay(PANEL_HIDE_MS)
                val bitmap = screenCapture.captureBitmap()
                overlay.show()
                val file = withContext(Dispatchers.IO) { writePng(bitmap) }
                overlay.setStatus(getString(R.string.overlay_saved, file.name))
            } catch (e: Exception) {
                Log.w(TAG, "Saving a screenshot failed", e)
                overlay.show()
                overlay.setStatus(getString(R.string.overlay_capture_failed))
            }
        }
    }

    private fun writePng(bitmap: Bitmap): File {
        val directory = File(getExternalFilesDir(null), CAPTURE_DIRECTORY).apply { mkdirs() }
        val file = File(directory, "capture-${LocalDateTime.now().format(FILE_TIME)}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    companion object {
        const val GAME_PACKAGE = "com.block.juggle"
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val TAG = "BBBot"
        private const val CAPTURE_DIRECTORY = "captures"
        private const val PANEL_HIDE_MS = 150L
        private val FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")

        private val mutableConnected = MutableStateFlow(false)

        /** Whether the service is switched on in the system settings and running. */
        val connected: StateFlow<Boolean> = mutableConnected
    }
}
