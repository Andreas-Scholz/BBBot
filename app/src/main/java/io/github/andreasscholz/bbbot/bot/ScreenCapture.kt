package io.github.andreasscholz.bbbot.bot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.Display
import io.github.andreasscholz.bbbot.vision.Screenshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ScreenCaptureException(val errorCode: Int) : Exception("Screenshot failed with error $errorCode")

/** Takes screenshots through the accessibility service, no faster than Android allows. */
class ScreenCapture(private val service: AccessibilityService) {

    private val mutex = Mutex()
    private var lastCapture = 0L

    suspend fun capture(): Screenshot {
        val bitmap = mutex.withLock {
            // Android rejects a screenshot requested too soon after the previous one.
            delay(lastCapture + MIN_INTERVAL_MS - SystemClock.uptimeMillis())
            try {
                request()
            } finally {
                lastCapture = SystemClock.uptimeMillis()
            }
        }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return Screenshot(bitmap.width, bitmap.height, pixels).also { bitmap.recycle() }
    }

    private suspend fun request(): Bitmap = suspendCancellableCoroutine { continuation ->
        val callback = object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val buffer = result.hardwareBuffer
                val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                // The hardware bitmap cannot be read pixel by pixel, a software copy can.
                val bitmap = hardware?.copy(Bitmap.Config.ARGB_8888, false)
                hardware?.recycle()
                buffer.close()
                if (bitmap != null) {
                    continuation.resume(bitmap)
                } else {
                    continuation.resumeWithException(ScreenCaptureException(ERROR_NO_BITMAP))
                }
            }

            override fun onFailure(errorCode: Int) {
                continuation.resumeWithException(ScreenCaptureException(errorCode))
            }
        }
        service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor, callback)
    }

    private companion object {
        /** Android allows a screenshot every third of a second. */
        const val MIN_INTERVAL_MS = 340L
        const val ERROR_NO_BITMAP = -1
    }
}
