package io.github.andreasscholz.bbbot.bot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GestureResultCallback
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.graphics.Path
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class GestureException(message: String) : Exception(message)

/**
 * One finger on the screen, moved step by step. A drag is sent as a chain of continued strokes,
 * so the finger can rest between the steps while it stays down, for example to let the game
 * catch up or to take a screenshot with the piece in the air.
 */
class Finger(private val service: AccessibilityService) {

    private var stroke: StrokeDescription? = null
    private var x = 0f
    private var y = 0f

    val isDown: Boolean get() = stroke != null

    /** Puts the finger down at [x], [y] and keeps it there for [durationMs]. */
    suspend fun down(x: Float, y: Float, durationMs: Long) {
        check(!isDown) { "Finger is already down" }
        this.x = x
        this.y = y
        send(StrokeDescription(pathTo(x, y), 0, durationMs, true))
    }

    /** Slides the finger in a straight line to [x], [y]. */
    suspend fun moveTo(x: Float, y: Float, durationMs: Long) {
        val previous = checkNotNull(stroke) { "Finger is not down" }
        val path = pathTo(x, y)
        this.x = x
        this.y = y
        send(previous.continueStroke(path, 0, durationMs, true))
    }

    /** Keeps the finger where it is for [durationMs] and then lifts it. */
    suspend fun up(durationMs: Long) {
        val previous = checkNotNull(stroke) { "Finger is not down" }
        send(previous.continueStroke(pathTo(x, y), 0, durationMs, false))
    }

    /** Lifts the finger if it is down, for cleaning up after a failure. */
    suspend fun release() {
        if (isDown) runCatching { up(durationMs = 1) }
        stroke = null
    }

    private fun pathTo(toX: Float, toY: Float) = Path().apply {
        moveTo(x, y)
        lineTo(toX, toY)
    }

    private suspend fun send(next: StrokeDescription) {
        val gesture = GestureDescription.Builder().addStroke(next).build()
        val completed = suspendCancellableCoroutine { continuation ->
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription) = continuation.resume(true)
                override fun onCancelled(gestureDescription: GestureDescription) = continuation.resume(false)
            }
            if (!service.dispatchGesture(gesture, callback, null)) continuation.resume(false)
        }
        // A cancelled gesture lifts the finger, whatever was planned.
        stroke = if (completed && next.willContinue()) next else null
        if (!completed) throw GestureException("Gesture was cancelled")
    }
}
