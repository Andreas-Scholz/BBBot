package io.github.andreasscholz.bbbot.vision

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * A tool rather than a test: prints what the reader sees in screenshots pulled from the phone.
 *
 * ```sh
 * BBBOT_SCREENSHOTS=/path/to/captures ./gradlew :app:testDebugUnitTest --tests '*ScreenshotDump*' -i
 * ```
 */
class ScreenshotDump {

    @Test
    fun `print what the reader sees`() {
        val directory = System.getenv("BBBOT_SCREENSHOTS")
        assumeTrue("BBBOT_SCREENSHOTS is not set", directory != null)

        val reader = ScreenReader(onReject = { println("rejected: $it") })
        for (file in File(directory).listFiles { file -> file.extension == "png" }.orEmpty().sorted()) {
            val image = ImageIO.read(file)
            val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
            val screen = reader.read(Screenshot(image.width, image.height, pixels))
            println("== ${file.name}")
            if (screen == null) {
                println("no game")
                continue
            }
            println(screen.geometry)
            println(screen.board)
            for (piece in screen.tray) {
                println("slot ${piece.slot} at ${piece.centerX}, ${piece.centerY}:\n${piece.piece}")
            }
        }
    }
}
