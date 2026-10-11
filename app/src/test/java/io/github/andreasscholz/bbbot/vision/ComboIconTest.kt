package io.github.andreasscholz.bbbot.vision

import org.junit.Assert.assertTrue
import org.junit.Test
import javax.imageio.ImageIO

class ComboIconTest {

    @Test
    fun `no symbol behind the score at the start of a game`() {
        for (name in listOf("wood-start.png", "wood-two-pieces-left.png", "marble-with-panel.png")) {
            val share = ComboIcon.share(load(name))
            assertTrue("$name: $share", share < ComboIcon.PRESENT)
        }
    }

    @Test
    fun `symbol behind the score while a combo runs, whatever the theme`() {
        val running = listOf(
            "pink-with-panel.png", "pink-lines-of-five.png", "purple-thin-border.png",
            "fur-empty-board.png", "gingham-pink-piece.png", "grey-marble-vein.png",
        )
        for (name in running) {
            val share = ComboIcon.share(load(name))
            assertTrue("$name: $share", share > ComboIcon.PRESENT)
        }
    }

    private fun load(name: String): Screenshot {
        val image = ImageIO.read(checkNotNull(javaClass.getResource("/screens/$name")) { "Missing $name" })
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        return Screenshot(image.width, image.height, pixels)
    }
}
