package app.commonplace.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QuoteImportTest {

    private val existing = listOf(
        Quote("a", "Already here", null),
        Quote("b", "Also here", "Ann"),
    )

    // --- replace ---

    @Test
    fun `replace makes the backup the whole collection`() {
        val incoming = listOf(Quote("x", "From the backup", null))
        val summary = QuoteImport.apply(existing, incoming, ImportMode.REPLACE)

        assertEquals(incoming, summary.quotes)
        assertEquals(1, summary.imported)
    }

    @Test
    fun `replace with an empty backup empties the collection`() {
        val summary = QuoteImport.apply(existing, emptyList(), ImportMode.REPLACE)
        assertEquals(emptyList<Quote>(), summary.quotes)
        assertEquals(0, summary.totalInFile)
    }

    // --- merge: the mode that stops a restore destroying newer quotes ---

    @Test
    fun `merge keeps everything already there and adds what is new`() {
        val incoming = listOf(Quote("x", "Brand new", null))
        val summary = QuoteImport.apply(existing, incoming, ImportMode.MERGE)

        assertEquals(existing + incoming, summary.quotes)
        assertEquals(1, summary.imported)
    }

    @Test
    fun `merge skips a quote whose text is already present, even under a different id`() {
        val incoming = listOf(Quote("different-id", "Already here", null))
        val summary = QuoteImport.apply(existing, incoming, ImportMode.MERGE)

        assertEquals(existing, summary.quotes)
        assertEquals(0, summary.imported)
        assertEquals(1, summary.skippedDuplicate)
    }

    @Test
    fun `merge ignores case and spacing when deciding what is a duplicate`() {
        val incoming = listOf(Quote("z", "  already   HERE  ", null))
        val summary = QuoteImport.apply(existing, incoming, ImportMode.MERGE)

        assertEquals(existing, summary.quotes)
        assertEquals(1, summary.skippedDuplicate)
    }

    @Test
    fun `merging the same backup twice adds nothing the second time`() {
        val incoming = listOf(Quote("x", "Brand new", null))

        val first = QuoteImport.apply(existing, incoming, ImportMode.MERGE)
        val second = QuoteImport.apply(first.quotes, incoming, ImportMode.MERGE)

        assertEquals(first.quotes, second.quotes, "a repeated restore must be a no-op")
        assertEquals(0, second.imported)
        assertEquals(1, second.skippedDuplicate)
    }

    // --- counting, so nothing is ever dropped silently ---

    @Test
    fun `the summary accounts for every quote in the file`() {
        val incoming = listOf(
            Quote("x", "Brand new", null),
            Quote("y", "   ", null),
            Quote("z", "Already here", null),
        )
        val summary = QuoteImport.apply(existing, incoming, ImportMode.MERGE)

        assertEquals(1, summary.imported)
        assertEquals(1, summary.skippedInvalid)
        assertEquals(1, summary.skippedDuplicate)
        assertEquals(incoming.size, summary.totalInFile, "every entry must be accounted for")
    }

    @Test
    fun `duplicates inside the file itself are counted, not stored twice`() {
        val incoming = listOf(
            Quote("x", "Same line", null),
            Quote("y", "Same line", null),
        )
        val summary = QuoteImport.apply(emptyList(), incoming, ImportMode.REPLACE)

        assertEquals(1, summary.quotes.size)
        assertEquals(1, summary.imported)
        assertEquals(1, summary.skippedDuplicate)
    }

    @Test
    fun `a repeated id inside the file is not stored twice`() {
        val incoming = listOf(
            Quote("same-id", "One line", null),
            Quote("same-id", "A different line", null),
        )
        val summary = QuoteImport.apply(emptyList(), incoming, ImportMode.REPLACE)

        assertEquals(1, summary.quotes.size)
        assertEquals(1, summary.skippedDuplicate)
    }

    @Test
    fun `imported quotes are cleaned on the way in`() {
        val incoming = listOf(Quote("x", "  padded  ", "  Ann  "))
        val summary = QuoteImport.apply(emptyList(), incoming, ImportMode.REPLACE)

        assertEquals(listOf(Quote("x", "padded", "Ann")), summary.quotes)
    }

    @Test
    fun `an over-long quote is skipped as invalid rather than stored`() {
        val incoming = listOf(Quote("x", "y".repeat(QuoteValidation.MAX_TEXT_LENGTH + 1), null))
        val summary = QuoteImport.apply(emptyList(), incoming, ImportMode.REPLACE)

        assertEquals(emptyList<Quote>(), summary.quotes)
        assertEquals(1, summary.skippedInvalid)
    }

    @Test
    fun `an entirely invalid file leaves a merge target untouched`() {
        val incoming = listOf(Quote("x", "  ", null), Quote("y", "", null))
        val summary = QuoteImport.apply(existing, incoming, ImportMode.MERGE)

        assertEquals(existing, summary.quotes)
        assertEquals(0, summary.imported)
        assertEquals(2, summary.skippedInvalid)
    }
}
