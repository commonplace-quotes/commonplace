package app.commonplace.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.random.Random

class RotationTest {

    private val fixedRandom get() = Random(1234)

    // --- empty and single-quote books: the two shapes that used to crash naive versions ---

    @ParameterizedTest(name = "empty book returns NO_QUOTE in {0} mode")
    @ValueSource(strings = ["SEQUENTIAL", "SHUFFLE"])
    fun `an empty book has no next quote`(mode: String) {
        val result = Rotation.next(0, Rotation.NO_QUOTE, OrderMode.valueOf(mode), fixedRandom)
        assertEquals(Rotation.NO_QUOTE, result)
    }

    @ParameterizedTest(name = "single-quote book stays on index 0 in {0} mode")
    @ValueSource(strings = ["SEQUENTIAL", "SHUFFLE"])
    fun `a single-quote book always returns that quote`(mode: String) {
        // Shuffle cannot honour "never repeat" here; it must return the quote, not spin.
        repeat(50) {
            assertEquals(0, Rotation.next(1, 0, OrderMode.valueOf(mode), Random(it)))
        }
    }

    // --- sequential ---

    @Test
    fun `sequential advances by one`() {
        assertEquals(1, Rotation.next(5, 0, OrderMode.SEQUENTIAL, fixedRandom))
        assertEquals(2, Rotation.next(5, 1, OrderMode.SEQUENTIAL, fixedRandom))
    }

    @Test
    fun `sequential wraps at the end`() {
        assertEquals(0, Rotation.next(3, 2, OrderMode.SEQUENTIAL, fixedRandom))
    }

    @Test
    fun `sequential visits every quote exactly once per lap`() {
        val count = 7
        var cursor = Rotation.NO_QUOTE
        val visited = mutableListOf<Int>()
        repeat(count) {
            cursor = Rotation.next(count, cursor, OrderMode.SEQUENTIAL, fixedRandom)
            visited += cursor
        }
        assertEquals((0 until count).toList(), visited)
    }

    // --- stale cursors, the state left behind when quotes are deleted ---

    @ParameterizedTest(name = "cursor {0} in a 3-quote book restarts the rotation")
    @ValueSource(ints = [3, 4, 99, -1, -5, Int.MAX_VALUE, Int.MIN_VALUE])
    fun `a cursor outside the book restarts at the first quote`(staleCursor: Int) {
        assertEquals(0, Rotation.next(3, staleCursor, OrderMode.SEQUENTIAL, fixedRandom))
    }

    @ParameterizedTest(name = "shuffle survives stale cursor {0}")
    @ValueSource(ints = [3, 4, 99, -1, Int.MAX_VALUE, Int.MIN_VALUE])
    fun `shuffle with a stale cursor still returns a real index`(staleCursor: Int) {
        val result = Rotation.next(3, staleCursor, OrderMode.SHUFFLE, fixedRandom)
        assertTrue(result in 0 until 3, "expected a valid index, got $result")
    }

    // --- shuffle ---

    @Test
    fun `shuffle never returns the quote already showing`() {
        // Every seed, every starting position, in a book big enough for a repeat to be likely.
        val count = 4
        repeat(300) { seed ->
            for (current in 0 until count) {
                val next = Rotation.next(count, current, OrderMode.SHUFFLE, Random(seed))
                assertNotEquals(current, next, "seed=$seed current=$current repeated the quote")
            }
        }
    }

    @Test
    fun `shuffle always returns an index inside the book`() {
        val count = 6
        repeat(500) { seed ->
            val next = Rotation.next(count, seed % count, OrderMode.SHUFFLE, Random(seed))
            assertTrue(next in 0 until count, "seed=$seed produced out-of-range index $next")
        }
    }

    @Test
    fun `shuffle can reach every other quote`() {
        val count = 5
        val current = 2
        val reached = (0 until 400)
            .map { Rotation.next(count, current, OrderMode.SHUFFLE, Random(it)) }
            .toSet()
        assertEquals(setOf(0, 1, 3, 4), reached, "every quote except the current one should be reachable")
    }
}
