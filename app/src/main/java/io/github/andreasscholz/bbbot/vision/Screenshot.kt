package io.github.andreasscholz.bbbot.vision

/** A captured screen as ARGB pixels, row by row. Plain Kotlin so recognition can be unit tested. */
class Screenshot(val width: Int, val height: Int, val pixels: IntArray) {

    init {
        require(pixels.size == width * height) { "Expected ${width * height} pixels, got ${pixels.size}" }
    }

    operator fun get(x: Int, y: Int): Int = pixels[y * width + x]
}
