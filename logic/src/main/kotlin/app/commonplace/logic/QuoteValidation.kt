package app.commonplace.logic

/**
 * The single rule about what counts as a usable quote.
 *
 * Both the editor and the importer go through here, so a quote typed by hand and a quote
 * read from a backup file are held to exactly the same standard.
 */
object QuoteValidation {

    const val MAX_TEXT_LENGTH = 1000
    const val MAX_AUTHOR_LENGTH = 120

    sealed interface Result {
        /** Accepted. [text] and [author] are the cleaned values that should be stored. */
        data class Valid(val text: String, val author: String?) : Result

        /** Nothing but whitespace. */
        data object Blank : Result

        /** Longer than [MAX_TEXT_LENGTH] once trimmed. */
        data class TooLong(val length: Int) : Result
    }

    fun validate(text: String, author: String?): Result {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return Result.Blank
        if (cleanText.length > MAX_TEXT_LENGTH) return Result.TooLong(cleanText.length)

        val cleanAuthor = author?.trim()?.take(MAX_AUTHOR_LENGTH)?.ifEmpty { null }
        return Result.Valid(cleanText, cleanAuthor)
    }

    /** The cleaned quote, or null if it fails [validate]. Used when filtering an import. */
    fun clean(quote: Quote): Quote? =
        when (val result = validate(quote.text, quote.author)) {
            is Result.Valid -> quote.copy(text = result.text, author = result.author)
            else -> null
        }
}
