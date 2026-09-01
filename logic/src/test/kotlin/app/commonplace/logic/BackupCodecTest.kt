package app.commonplace.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class BackupCodecTest {

    private val book = listOf(
        Quote("a", "First quote", "Ann"),
        Quote("b", "Second quote", null),
    )

    // --- the round trip, which is the whole promise of the feature ---

    @Test
    fun `a book survives an encode-decode round trip unchanged`() {
        val decoded = BackupCodec.decode(BackupCodec.encode(book))
        assertEquals(BackupCodec.DecodeResult.Ok(book), decoded)
    }

    @Test
    fun `an empty book round trips`() {
        val decoded = BackupCodec.decode(BackupCodec.encode(emptyList()))
        assertEquals(BackupCodec.DecodeResult.Ok(emptyList<Quote>()), decoded)
    }

    @Test
    fun `newlines, quotes and backslashes survive the round trip`() {
        val awkward = listOf(
            Quote("n", "Line one\nLine two", null),
            Quote("q", """He said "hello" to me""", null),
            Quote("b", "back\\slash and \ttab", null),
        )
        assertEquals(BackupCodec.DecodeResult.Ok(awkward), BackupCodec.decode(BackupCodec.encode(awkward)))
    }

    @ParameterizedTest(name = "unicode survives the round trip [{0}]")
    @ValueSource(strings = ["日本語の引用", "Цитата", "نص عربي", "🌱 grow 🌻", "café"])
    fun `unicode survives the round trip`(text: String) {
        val one = listOf(Quote("u", text, null))
        assertEquals(BackupCodec.DecodeResult.Ok(one), BackupCodec.decode(BackupCodec.encode(one)))
    }

    @Test
    fun `the encoded file is readable text carrying the format tag and version`() {
        val encoded = BackupCodec.encode(book)
        assertTrue(encoded.contains(BackupCodec.FORMAT), "the file must identify itself")
        assertTrue(encoded.contains("\"version\""), "the file must carry a version")
        assertTrue(encoded.contains("First quote"), "the file must be human-readable, not encoded")
    }

    // --- every way a file can be wrong ---

    @ParameterizedTest(name = "blank input is malformed [{0}]")
    @ValueSource(strings = ["", "   ", "\n\n"])
    fun `an empty file is malformed`(raw: String) {
        assertInstanceOf(BackupCodec.DecodeResult.Malformed::class.java, BackupCodec.decode(raw))
    }

    @ParameterizedTest(name = "unparseable input is malformed [{0}]")
    @ValueSource(strings = ["not json at all", "{", "{\"format\":", "{{}}", "<xml/>"])
    fun `text that is not JSON is malformed`(raw: String) {
        assertInstanceOf(BackupCodec.DecodeResult.Malformed::class.java, BackupCodec.decode(raw))
    }

    @ParameterizedTest(name = "someone else's JSON is not our format [{0}]")
    @ValueSource(
        strings = [
            "[]",
            "[{\"text\":\"hi\"}]",
            "{}",
            "{\"quotes\":[]}",
            "{\"format\":\"someone.else\",\"version\":1,\"quotes\":[]}",
            "\"just a string\"",
            "42",
        ],
    )
    fun `valid JSON that is not a Commonplace backup is rejected as not ours`(raw: String) {
        assertEquals(BackupCodec.DecodeResult.NotOurFormat, BackupCodec.decode(raw))
    }

    @Test
    fun `a format field that is not a string is not our format`() {
        val raw = """{"format":{"nested":true},"version":1,"quotes":[]}"""
        assertEquals(BackupCodec.DecodeResult.NotOurFormat, BackupCodec.decode(raw))
    }

    @Test
    fun `a backup with no version number is malformed`() {
        val raw = """{"format":"${BackupCodec.FORMAT}","quotes":[]}"""
        assertInstanceOf(BackupCodec.DecodeResult.Malformed::class.java, BackupCodec.decode(raw))
    }

    @Test
    fun `a backup from a newer app version is reported as unsupported, not as broken`() {
        val raw = """{"format":"${BackupCodec.FORMAT}","version":99,"quotes":[]}"""
        assertEquals(BackupCodec.DecodeResult.UnsupportedVersion(99), BackupCodec.decode(raw))
    }

    @Test
    fun `a quote missing its text is malformed`() {
        val raw = """{"format":"${BackupCodec.FORMAT}","version":1,"quotes":[{"id":"a"}]}"""
        assertInstanceOf(BackupCodec.DecodeResult.Malformed::class.java, BackupCodec.decode(raw))
    }

    @Test
    fun `a null quote text is malformed rather than silently becoming empty`() {
        val raw = """{"format":"${BackupCodec.FORMAT}","version":1,"quotes":[{"id":"a","text":null}]}"""
        assertInstanceOf(BackupCodec.DecodeResult.Malformed::class.java, BackupCodec.decode(raw))
    }

    // --- forgiving where it costs nothing ---

    @Test
    fun `a leading byte-order mark from a Windows editor is tolerated`() {
        val withBom = "\uFEFF" + BackupCodec.encode(book)
        assertEquals(BackupCodec.DecodeResult.Ok(book), BackupCodec.decode(withBom))
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        val padded = "\n\n  " + BackupCodec.encode(book) + "  \n"
        assertEquals(BackupCodec.DecodeResult.Ok(book), BackupCodec.decode(padded))
    }

    @Test
    fun `fields written by a newer version are ignored rather than failing the restore`() {
        val raw = """
            {
              "format": "${BackupCodec.FORMAT}",
              "version": 1,
              "exportedAt": "2026-09-01T00:00:00Z",
              "quotes": [{"id":"a","text":"Hello","colour":"blue"}]
            }
        """.trimIndent()
        assertEquals(
            BackupCodec.DecodeResult.Ok(listOf(Quote("a", "Hello", null))),
            BackupCodec.decode(raw),
        )
    }

    @Test
    fun `an author is optional`() {
        val raw = """{"format":"${BackupCodec.FORMAT}","version":1,"quotes":[{"id":"a","text":"Hello"}]}"""
        assertEquals(BackupCodec.DecodeResult.Ok(listOf(Quote("a", "Hello", null))), BackupCodec.decode(raw))
    }
}
