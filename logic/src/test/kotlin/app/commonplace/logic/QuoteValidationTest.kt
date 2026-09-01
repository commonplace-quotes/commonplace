package app.commonplace.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class QuoteValidationTest {

    @Test
    fun `accepts an ordinary quote and returns it trimmed`() {
        val result = QuoteValidation.validate("  Be kind.  ", "  Anon  ")
        assertEquals(QuoteValidation.Result.Valid("Be kind.", "Anon"), result)
    }

    @ParameterizedTest(name = "rejects blank input [{0}]")
    @ValueSource(strings = ["", " ", "\t", "\n", "   \n\t  "])
    fun `rejects text that is empty or only whitespace`(text: String) {
        assertEquals(QuoteValidation.Result.Blank, QuoteValidation.validate(text, null))
    }

    @Test
    fun `rejects text longer than the cap, reporting the trimmed length`() {
        val tooLong = "x".repeat(QuoteValidation.MAX_TEXT_LENGTH + 1)
        val result = QuoteValidation.validate("  $tooLong  ", null)
        assertEquals(
            QuoteValidation.Result.TooLong(QuoteValidation.MAX_TEXT_LENGTH + 1),
            result,
            "the reported length must be of the trimmed text, not the raw input",
        )
    }

    @Test
    fun `accepts text of exactly the cap`() {
        val exact = "x".repeat(QuoteValidation.MAX_TEXT_LENGTH)
        assertEquals(QuoteValidation.Result.Valid(exact, null), QuoteValidation.validate(exact, null))
    }

    @Test
    fun `treats a whitespace-only author as no author`() {
        val result = QuoteValidation.validate("Something", "   ")
        assertEquals(QuoteValidation.Result.Valid("Something", null), result)
    }

    @Test
    fun `truncates an over-long author rather than rejecting the quote`() {
        val longAuthor = "a".repeat(QuoteValidation.MAX_AUTHOR_LENGTH + 50)
        val result = QuoteValidation.validate("Something", longAuthor)
        val valid = result as QuoteValidation.Result.Valid
        assertEquals(QuoteValidation.MAX_AUTHOR_LENGTH, valid.author!!.length)
    }

    @Test
    fun `keeps internal newlines, because a quote can be a stanza`() {
        val stanza = "Line one\nLine two"
        assertEquals(QuoteValidation.Result.Valid(stanza, null), QuoteValidation.validate(stanza, null))
    }

    @ParameterizedTest(name = "keeps non-latin and emoji text [{0}]")
    @ValueSource(strings = ["日本語の引用", "Цитата", "نص عربي", "🌱 grow", "Ω≈ç√∫"])
    fun `keeps unicode text intact`(text: String) {
        assertEquals(QuoteValidation.Result.Valid(text, null), QuoteValidation.validate(text, null))
    }

    @Test
    fun `clean returns a normalised quote`() {
        val cleaned = QuoteValidation.clean(Quote("id", "  spaced  ", "  Ann  "))
        assertEquals(Quote("id", "spaced", "Ann"), cleaned)
    }

    @Test
    fun `clean returns null for a quote that fails validation`() {
        assertNull(QuoteValidation.clean(Quote("id", "   ", null)))
    }
}
