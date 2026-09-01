package app.commonplace.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class WidgetQuoteTest {

    private val book = listOf(
        Quote("a", "First", "Ann"),
        Quote("b", "Second", null),
        Quote("c", "Third", "Cal"),
    )

    @Test
    fun `shows the quote at the cursor`() {
        assertEquals(WidgetQuote.Display.Shown("Second", null), WidgetQuote.select(book, 1))
    }

    @Test
    fun `carries the author through when there is one`() {
        assertEquals(WidgetQuote.Display.Shown("Third", "Cal"), WidgetQuote.select(book, 2))
    }

    @Test
    fun `an empty book shows the empty state`() {
        assertEquals(WidgetQuote.Display.Empty, WidgetQuote.select(emptyList(), 0))
    }

    @ParameterizedTest(name = "cursor {0} falls back to the first quote")
    @ValueSource(ints = [3, 4, 99, -1, Int.MAX_VALUE, Int.MIN_VALUE])
    fun `a cursor left over from a bigger book shows the first quote, not a blank widget`(cursor: Int) {
        // Deleting quotes must never make the widget look like the data is gone.
        assertEquals(WidgetQuote.Display.Shown("First", "Ann"), WidgetQuote.select(book, cursor))
    }

    @Test
    fun `an empty book with a stale cursor is still the empty state`() {
        assertEquals(WidgetQuote.Display.Empty, WidgetQuote.select(emptyList(), 7))
    }
}
