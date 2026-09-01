package app.commonplace.logic

import kotlin.random.Random

enum class OrderMode { SEQUENTIAL, SHUFFLE }

/**
 * Which quote a tap moves to.
 *
 * Total on every input — an empty book, a single quote, and a cursor left pointing past the
 * end after quotes were deleted all have defined answers, because the widget calls this on
 * the main thread and must never throw.
 */
object Rotation {

    /** No quote can be shown: the book is empty. */
    const val NO_QUOTE = -1

    fun next(count: Int, currentIndex: Int, mode: OrderMode, random: Random): Int {
        if (count <= 0) return NO_QUOTE
        if (count == 1) return 0

        // A cursor from a larger book, or a not-yet-set cursor, restarts the rotation.
        val current = if (currentIndex in 0 until count) currentIndex else NO_QUOTE

        return when (mode) {
            OrderMode.SEQUENTIAL -> if (current == NO_QUOTE) 0 else (current + 1) % count
            OrderMode.SHUFFLE -> shuffleAwayFrom(count, current, random)
        }
    }

    /**
     * Picks uniformly from the quotes that are *not* the current one, in one draw.
     *
     * Drawing from `count - 1` and stepping over the current index is what makes this
     * terminate: a retry loop would spin forever on a book where every index is excluded.
     */
    private fun shuffleAwayFrom(count: Int, current: Int, random: Random): Int {
        if (current == NO_QUOTE) return random.nextInt(count)
        val drawn = random.nextInt(count - 1)
        return if (drawn >= current) drawn + 1 else drawn
    }
}
