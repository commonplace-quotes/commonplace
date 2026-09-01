package app.commonplace.logic

/**
 * What a widget shows for a given cursor.
 *
 * This is deliberately a pure function rather than logic inside the widget provider: it is
 * the answer to "which quote is on the home screen right now", and it can be asserted
 * directly instead of through a RemoteViews shadow.
 */
object WidgetQuote {

    sealed interface Display {
        data class Shown(val text: String, val author: String?) : Display

        /** The book has no quotes yet — the widget shows its invitation instead. */
        data object Empty : Display
    }

    fun select(book: List<Quote>, cursor: Int): Display {
        if (book.isEmpty()) return Display.Empty

        // A cursor left over from a larger book shows the first quote rather than blanking
        // the widget, which would look like data loss to someone who just deleted a quote.
        val index = if (cursor in book.indices) cursor else 0
        val quote = book[index]
        return Display.Shown(quote.text, quote.author)
    }
}
