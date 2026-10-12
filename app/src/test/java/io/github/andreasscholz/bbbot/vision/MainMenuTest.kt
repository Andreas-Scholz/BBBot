package io.github.andreasscholz.bbbot.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class MainMenuTest {

    @Test
    fun `finds the button that goes on with the game`() {
        val (x, y) = MainMenu.classicButton(load("main-menu.png"))!!
        assertEquals(540, x)
        assertEquals(1512, y)
    }

    @Test
    fun `takes no screen of a game for the menu`() {
        val screens = File(javaClass.getResource("/screens")!!.toURI()).listFiles()!!.filter { it.name != "main-menu.png" }
        for (file in screens) assertNull(file.name, MainMenu.classicButton(load(file.name)))
    }

    private fun load(name: String): Screenshot {
        val image = ImageIO.read(javaClass.getResource("/screens/$name"))
        return Screenshot(image.width, image.height, image.getRGB(0, 0, image.width, image.height, null, 0, image.width))
    }
}
