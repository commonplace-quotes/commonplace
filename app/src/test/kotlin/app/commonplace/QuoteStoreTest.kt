package app.commonplace

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.commonplace.logic.BackupCodec
import app.commonplace.logic.Quote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class QuoteStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val store: QuoteStore get() = QuoteStore(context)

    private val quotesFile: File get() = File(context.filesDir, "quotes.json")
    private val backupFile: File get() = File(context.filesDir, "quotes.json.bak")

    private val bookOne = listOf(Quote("a", "First", "Ann"))
    private val bookTwo = listOf(Quote("a", "First", "Ann"), Quote("b", "Second", null))

    @Test
    fun `a fresh install has no quotes and does not crash`() {
        assertEquals(emptyList<Quote>(), store.load())
    }

    @Test
    fun `saved quotes come back exactly`() {
        store.save(bookTwo)
        assertEquals(bookTwo, store.load())
    }

    @Test
    fun `saving replaces the previous collection rather than appending`() {
        store.save(bookTwo)
        store.save(bookOne)

        assertEquals(bookOne, store.load())
    }

    @Test
    fun `an empty collection can be stored`() {
        store.save(bookTwo)
        store.save(emptyList())

        assertEquals(emptyList<Quote>(), store.load())
    }

    @Test
    fun `the stored file is the documented backup format`() {
        store.save(bookOne)

        val decoded = BackupCodec.decode(quotesFile.readText())
        assertEquals(BackupCodec.DecodeResult.Ok(bookOne), decoded)
    }

    // --- the safety net ---

    @Test
    fun `the previous collection is kept as a backup copy`() {
        store.save(bookOne)
        store.save(bookTwo)

        assertTrue("a backup copy should exist after the second save", backupFile.exists())
        assertEquals(BackupCodec.DecodeResult.Ok(bookOne), BackupCodec.decode(backupFile.readText()))
    }

    @Test
    fun `a corrupted main file falls back to the backup copy instead of losing everything`() {
        store.save(bookOne)
        store.save(bookTwo)

        quotesFile.writeText("{ this is not json")

        assertEquals(bookOne, store.load())
    }

    @Test
    fun `a truncated main file falls back to the backup copy`() {
        store.save(bookOne)
        store.save(bookTwo)

        quotesFile.writeText(BackupCodec.encode(bookTwo).take(20))

        assertEquals(bookOne, store.load())
    }

    @Test
    fun `an empty main file falls back to the backup copy`() {
        store.save(bookOne)
        store.save(bookTwo)

        quotesFile.writeText("")

        assertEquals(bookOne, store.load())
    }

    @Test
    fun `a corrupt file with no backup yet reads as empty rather than throwing`() {
        quotesFile.writeText("garbage")

        assertEquals(emptyList<Quote>(), store.load())
    }

    @Test
    fun `an implausibly large file is refused rather than loaded into memory`() {
        quotesFile.writeBytes(ByteArray(BackupCodec.MAX_BYTES + 1))

        assertEquals(emptyList<Quote>(), store.load())
    }

    @Test
    fun `saving after a corruption repairs the main file`() {
        store.save(bookOne)
        quotesFile.writeText("garbage")

        store.save(bookTwo)

        assertEquals(bookTwo, store.load())
    }

    @Test
    fun `unicode and newlines survive a real round trip through the file`() {
        val awkward = listOf(Quote("u", "Line one\nLine two — 日本語 🌱", "Ann"))
        store.save(awkward)

        assertEquals(awkward, store.load())
    }

    @Test
    fun `no temporary file is left behind after a save`() {
        store.save(bookTwo)

        assertTrue(
            "the temp file must be renamed away, not left in the data directory",
            !File(context.filesDir, "quotes.json.tmp").exists(),
        )
    }
}
