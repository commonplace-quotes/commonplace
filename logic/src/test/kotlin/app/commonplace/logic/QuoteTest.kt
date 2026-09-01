package app.commonplace.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class QuoteTest {

    @Test
    fun `fingerprint ignores surrounding whitespace`() {
        assertEquals(Quote("a", "Be kind.").fingerprint, Quote("b", "   Be kind.  ").fingerprint)
    }

    @Test
    fun `fingerprint ignores case`() {
        assertEquals(Quote("a", "Be Kind.").fingerprint, Quote("b", "be kind.").fingerprint)
    }

    @Test
    fun `fingerprint collapses runs of whitespace, including newlines`() {
        assertEquals(Quote("a", "Be    kind").fingerprint, Quote("b", "Be\n\tkind").fingerprint)
    }

    @Test
    fun `fingerprint still distinguishes genuinely different quotes`() {
        assertNotEquals(Quote("a", "Be kind").fingerprint, Quote("b", "Be brave").fingerprint)
    }

    @Test
    fun `fingerprint ignores the author, because the words are what repeat`() {
        assertEquals(Quote("a", "Be kind", "Ann").fingerprint, Quote("b", "Be kind", "Bob").fingerprint)
    }
}
