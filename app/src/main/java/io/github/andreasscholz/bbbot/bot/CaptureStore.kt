package io.github.andreasscholz.bbbot.bot

import android.content.Context
import android.graphics.Bitmap
import io.github.andreasscholz.bbbot.vision.Screenshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Keeps screenshots as PNG files in the app's folder on the shared storage, where `adb pull`
 * reaches them. They are what the recognition is developed and tested against.
 */
class CaptureStore(context: Context) {

    private val directory = File(context.getExternalFilesDir(null), "captures")

    /** Saves [shot] as `<prefix>-<time>.png` and drops the oldest files beyond [MAX_FILES]. */
    suspend fun save(shot: Screenshot, prefix: String): File = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val file = File(directory, "$prefix-${LocalDateTime.now().format(FILE_TIME)}.png")
        val bitmap = Bitmap.createBitmap(shot.pixels, shot.width, shot.height, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        directory.listFiles().orEmpty().sortedByDescending { it.lastModified() }.drop(MAX_FILES).forEach { it.delete() }
        file
    }

    private companion object {
        const val MAX_FILES = 40
        val FILE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")
    }
}
