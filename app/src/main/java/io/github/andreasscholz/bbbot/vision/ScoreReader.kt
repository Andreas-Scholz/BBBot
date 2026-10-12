package io.github.andreasscholz.bbbot.vision

import kotlin.math.abs

/**
 * Reads the score off a screenshot. The game draws it in large digits of one font in every
 * theme, white or nearly white. The digits are found as what has the most common bright color
 * in the strip where the score stands, cut apart at the gaps between them, and each compared
 * to a pattern of what it should look like. The patterns were made from screenshots with known
 * scores.
 *
 * Going by the most common bright color, and not by anything white, keeps other bright things
 * out: in one theme the combo symbol behind the score is a fried egg with a cream rim. Only
 * when that finds no score is anything bright and grey taken for part of a digit.
 *
 * Nothing is returned rather than a guess: when a digit matches no pattern well, when something
 * else white is in the way, or when there is no score.
 */
object ScoreReader {

    fun read(shot: Screenshot): Long? = read(shot, shaded = false) ?: read(shot, shaded = true)

    /**
     * With [shaded], everything bright and grey counts as part of a digit, not only what has
     * the digits' most common color: one theme draws them like metal, white at the top and
     * grey at the bottom. That is tried second, because more can get in its way.
     */
    private fun read(shot: Screenshot, shaded: Boolean): Long? {
        val top = (shot.height * TOP).toInt()
        val bottom = (shot.height * BOTTOM).toInt()
        val height = bottom - top
        // The color of the digits: the most common one among the bright, nearly grey pixels.
        val counts = IntArray(COLOR_BINS * COLOR_BINS * COLOR_BINS)
        for (y in 0 until height) {
            for (x in 0 until shot.width) {
                val pixel = shot[x, top + y]
                val red = pixel shr 16 and 0xFF
                val green = pixel shr 8 and 0xFF
                val blue = pixel and 0xFF
                val low = minOf(red, green, blue)
                if (low >= WHITE && maxOf(red, green, blue) - low <= WHITE_SPREAD) {
                    counts[((red shr COLOR_SHIFT) * COLOR_BINS + (green shr COLOR_SHIFT)) * COLOR_BINS + (blue shr COLOR_SHIFT)]++
                }
            }
        }
        val bin = counts.indices.maxBy { counts[it] }
        if (counts[bin] == 0) return null
        val half = 1 shl (COLOR_SHIFT - 1)
        val digitRed = (bin / (COLOR_BINS * COLOR_BINS) shl COLOR_SHIFT) + half
        val digitGreen = (bin / COLOR_BINS % COLOR_BINS shl COLOR_SHIFT) + half
        val digitBlue = (bin % COLOR_BINS shl COLOR_SHIFT) + half

        val white = BooleanArray(shot.width * height)
        val perRow = IntArray(height)
        for (y in 0 until height) {
            for (x in 0 until shot.width) {
                val pixel = shot[x, top + y]
                val red = pixel shr 16 and 0xFF
                val green = pixel shr 8 and 0xFF
                val blue = pixel and 0xFF
                val distance = abs(red - digitRed) + abs(green - digitGreen) + abs(blue - digitBlue)
                val low = minOf(red, green, blue)
                val grey = shaded && low >= SHADED && maxOf(red, green, blue) - low <= SHADED_SPREAD
                if (distance <= DIGIT_COLOR_DISTANCE || grey) {
                    white[y * shot.width + x] = true
                    perRow[y]++
                }
            }
        }

        // The digits stand in the tallest run of rows with a fair amount of their color.
        val fullest = perRow.max()
        val (bandTop, bandBottom) =
            longestRun(height) { perRow[it] >= maxOf(shot.width / 135, fullest / 4) } ?: return null
        val bandHeight = bandBottom - bandTop + 1
        if (bandHeight < shot.height * MIN_DIGIT_HEIGHT) return null

        val perColumn = IntArray(shot.width)
        for (y in bandTop..bandBottom) {
            for (x in 0 until shot.width) if (white[y * shot.width + x]) perColumn[x]++
        }
        val minInColumn = maxOf(2, bandHeight / 12)

        var score = 0L
        var digits = 0
        var x = 0
        while (x < shot.width) {
            if (perColumn[x] < minInColumn) {
                x++
                continue
            }
            val left = x
            while (x < shot.width && perColumn[x] >= minInColumn) x++
            val right = x - 1
            // Specks of white, a sparkle on the combo symbol for example, are no digits.
            if (right - left + 1 < bandHeight * MIN_DIGIT_WIDTH) continue
            // Digits that run into each other or into something else cannot be told apart.
            if (right - left + 1 > bandHeight * MAX_DIGIT_WIDTH) return null
            val digit = digitAt(white, shot.width, left, right, bandTop, bandBottom) ?: return null
            score = score * 10 + digit
            if (++digits > MAX_DIGITS) return null
        }
        return if (digits == 0) null else score
    }

    private fun digitAt(white: BooleanArray, width: Int, left: Int, right: Int, bandTop: Int, bandBottom: Int): Int? {
        // A one is recognized by being narrow; stretched to the grid it would look like a block.
        if ((right - left + 1).toFloat() / (bandBottom - bandTop + 1) < ONE_ASPECT) return 1

        var top = bandTop
        var bottom = bandBottom
        fun emptyRow(y: Int) = (left..right).none { white[y * width + it] }
        while (top < bottom && emptyRow(top)) top++
        while (bottom > top && emptyRow(bottom)) bottom--

        val glyphWidth = right - left + 1
        val glyphHeight = bottom - top + 1
        var best = -1
        var bestDistance = Int.MAX_VALUE
        var secondDistance = Int.MAX_VALUE
        for ((digit, pattern) in PATTERNS) {
            var distance = 0
            for (row in 0 until GRID_HEIGHT) {
                val y0 = row * glyphHeight / GRID_HEIGHT
                val y1 = maxOf((row + 1) * glyphHeight / GRID_HEIGHT, y0 + 1)
                for (col in 0 until GRID_WIDTH) {
                    val x0 = col * glyphWidth / GRID_WIDTH
                    val x1 = maxOf((col + 1) * glyphWidth / GRID_WIDTH, x0 + 1)
                    var set = 0
                    for (y in y0 until y1) {
                        for (x in x0 until x1) if (white[(top + y) * width + left + x]) set++
                    }
                    val filled = set * 2 >= (y1 - y0) * (x1 - x0)
                    if (filled != (pattern[row][col] == '#')) distance++
                }
            }
            if (distance < bestDistance) {
                secondDistance = bestDistance
                bestDistance = distance
                best = digit
            } else if (distance < secondDistance) {
                secondDistance = distance
            }
        }
        if (bestDistance > MAX_DISTANCE || secondDistance - bestDistance < MIN_MARGIN) return null
        return best
    }

    /** The longest run of indices below [size] for which [test] holds, as first and last index. */
    private inline fun longestRun(size: Int, test: (Int) -> Boolean): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var start = -1
        for (index in 0..size) {
            val on = index < size && test(index)
            if (on && start < 0) {
                start = index
            } else if (!on && start >= 0) {
                if (best == null || index - 1 - start > best.second - best.first) best = start to index - 1
                start = -1
            }
        }
        return best
    }

    // The strip of the screen in which the score stands, as fractions of its height.
    const val TOP = 0.105f
    const val BOTTOM = 0.215f

    private const val WHITE = 190
    private const val WHITE_SPREAD = 40

    // The darkest and the most colorful a shaded digit gets.
    private const val SHADED = 165
    private const val SHADED_SPREAD = 20

    // Colors are counted in cubes of 8 × 8 × 8.
    private const val COLOR_SHIFT = 3
    private const val COLOR_BINS = 256 shr COLOR_SHIFT

    /** How far a pixel may be from the color of the digits, summed over red, green and blue. */
    private const val DIGIT_COLOR_DISTANCE = 30
    private const val MIN_DIGIT_HEIGHT = 0.03f

    /** The width of the narrowest thing taken for a digit, as a fraction of the digits' height. */
    private const val MIN_DIGIT_WIDTH = 0.15f
    private const val MAX_DIGIT_WIDTH = 0.95f
    private const val ONE_ASPECT = 0.5f
    private const val MAX_DIGITS = 12

    private const val GRID_WIDTH = 10
    private const val GRID_HEIGHT = 14

    /** The most cells of the grid a digit may differ from its pattern; the closest two patterns differ in 20. */
    private const val MAX_DISTANCE = 24

    /** How much closer a digit must be to its pattern than to the next best. */
    private const val MIN_MARGIN = 4

    private val PATTERNS: Map<Int, List<String>> = mapOf(
        0 to listOf("...####...", "..######..", ".###..###.", ".##....##.", "###....###", "###....###", "##......##", "##......##", "###....###", "###....###", ".##....##.", ".###..###.", "..######..", "...####..."),
        2 to listOf("...####...", "..#######.", ".###.####.", "###....###", ".#.....###", ".......###", "......###.", "......###.", ".....###..", "....###...", "...###....", "..###.....", "##########", "##########"),
        3 to listOf("...####...", ".########.", ".###..###.", ".##....###", ".......###", "......###.", "....####..", ".....####.", ".......###", ".......###", "##.....###", "###...####", ".########.", "..######.."),
        4 to listOf("......###.", ".....####.", ".....####.", "....#####.", "...######.", "...##.###.", "..###.###.", "..##..###.", ".###..###.", "##########", "##########", "......###.", "......###.", "......###."),
        5 to listOf("..#######.", ".########.", ".###......", ".###......", ".###......", ".#######..", ".########.", "......####", ".......###", ".......###", ".#.....###", "###...####", "#########.", "..#####..."),
        6 to listOf(".....###..", "....###...", "...###....", "...###....", "..###.....", ".#######..", ".########.", "###....###", "###....###", "###....###", "###....###", ".###..####", ".########.", "...#####.."),
        7 to listOf("##########", "##########", "......###.", "......###.", ".....###..", "....####..", "....###...", "...####...", "...###....", "..###.....", "..###.....", ".###......", ".###......", "###......."),
        8 to listOf("...####...", ".########.", ".###..###.", ".##....###", ".##....##.", ".###..###.", "..######..", ".########.", "###....###", "###....###", "###....###", "####..####", ".########.", "..######.."),
        9 to listOf("...####...", ".########.", "####..###.", "###....###", "###....###", "###....###", "###....###", ".#########", "..#######.", "....####..", "....####..", "....###...", "...###....", "..###....."),
    )
}
