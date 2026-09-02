package app.commonplace

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.commonplace.logic.BackupCodec
import app.commonplace.logic.Quote
import com.google.android.material.appbar.MaterialToolbar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Backup is the reason this app exists, and until now only its pure parts were covered —
 * the codec, the merge rules, the file writes. These tests drive the glue in between: the
 * menu opens the system picker, the picker hands back a Uri, and the app reads it.
 *
 * The picker itself is system UI and cannot be shown here, so the result is delivered the
 * way the framework delivers it, and the file behind the Uri is stubbed on the resolver.
 */
@RunWith(RobolectricTestRunner::class)
class BackupFlowTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val uri: Uri = Uri.parse("content://commonplace.test/backup.json")

    private fun launch() = Robolectric.buildActivity(MainActivity::class.java).setup().get()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    /** Puts [content] behind [uri], then runs the restore flow end to end. */
    private fun restore(activity: MainActivity, content: String) {
        shadowOf(activity.contentResolver).registerInputStream(uri, content.byteInputStream())

        activity.findViewById<MaterialToolbar>(R.id.toolbar)
            .menu.performIdentifierAction(R.id.action_restore, 0)
        idle()

        val started = shadowOf(activity).nextStartedActivityForResult
        assertNotNull("restore should have opened the file picker", started)
        shadowOf(activity).receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(uri))
        idle()
    }

    // --- the happy path ---

    @Test
    fun `restoring a backup onto a fresh install brings the quotes back`() {
        val activity = launch()

        restore(activity, BackupCodec.encode(listOf(Quote("a", "From the file", "Ann"))))

        val stored = QuoteStore(context).load()
        assertEquals(1, stored.size)
        assertEquals("From the file", stored.first().text)
        assertEquals("Ann", stored.first().author)
    }

    @Test
    fun `restoring several quotes brings all of them back`() {
        val backup = List(5) { Quote("id$it", "Quote number $it", null) }
        val activity = launch()

        restore(activity, BackupCodec.encode(backup))

        assertEquals(5, QuoteStore(context).load().size)
    }

    @Test
    fun `a restore onto an empty collection does not stop to ask about merging`() {
        val activity = launch()

        restore(activity, BackupCodec.encode(listOf(Quote("a", "Just restore it", null))))

        // Nothing to lose, so the user should not be made to answer a question.
        assertEquals(1, QuoteStore(context).load().size)
    }

    // --- the promise that matters: a bad file must never cost you your quotes ---

    @Test
    fun `restoring rubbish leaves the existing quotes untouched`() {
        val existing = listOf(Quote("keep", "Do not lose me", null))
        QuoteStore(context).save(existing)
        val activity = launch()

        restore(activity, "this is not a backup file at all")

        assertEquals(existing, QuoteStore(context).load())
    }

    @Test
    fun `restoring an empty file leaves the existing quotes untouched`() {
        val existing = listOf(Quote("keep", "Do not lose me", null))
        QuoteStore(context).save(existing)
        val activity = launch()

        restore(activity, "")

        assertEquals(existing, QuoteStore(context).load())
    }

    @Test
    fun `restoring somebody else's json leaves the existing quotes untouched`() {
        val existing = listOf(Quote("keep", "Do not lose me", null))
        QuoteStore(context).save(existing)
        val activity = launch()

        restore(activity, """{"format":"some.other.app","version":1,"quotes":[]}""")

        assertEquals(existing, QuoteStore(context).load())
    }

    @Test
    fun `a file bigger than the cap is refused and changes nothing`() {
        val existing = listOf(Quote("keep", "Do not lose me", null))
        QuoteStore(context).save(existing)
        val activity = launch()

        restore(activity, "x".repeat(BackupCodec.MAX_BYTES + 1))

        assertEquals(existing, QuoteStore(context).load())
    }

    @Test
    fun `a backup from a newer version is refused rather than half-applied`() {
        val existing = listOf(Quote("keep", "Do not lose me", null))
        QuoteStore(context).save(existing)
        val activity = launch()

        restore(activity, """{"format":"${BackupCodec.FORMAT}","version":99,"quotes":[]}""")

        assertEquals(existing, QuoteStore(context).load())
    }

    // --- export ---

    @Test
    fun `backing up opens the file picker`() {
        QuoteStore(context).save(listOf(Quote("a", "Worth keeping", null)))
        val activity = launch()

        activity.findViewById<MaterialToolbar>(R.id.toolbar)
            .menu.performIdentifierAction(R.id.action_backup, 0)
        idle()

        assertNotNull(
            "backup should have opened the file picker",
            shadowOf(activity).nextStartedActivityForResult,
        )
    }

    @Test
    fun `what gets written is a real backup that can be read straight back`() {
        val book = listOf(Quote("a", "Round trips", "Ann"), Quote("b", "Both of them", null))
        QuoteStore(context).save(book)
        val activity = launch()

        val written = java.io.ByteArrayOutputStream()
        shadowOf(activity.contentResolver).registerOutputStream(uri, written)

        activity.findViewById<MaterialToolbar>(R.id.toolbar)
            .menu.performIdentifierAction(R.id.action_backup, 0)
        idle()
        val started = shadowOf(activity).nextStartedActivityForResult
        shadowOf(activity).receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(uri))
        idle()

        val decoded = BackupCodec.decode(written.toString(Charsets.UTF_8.name()))
        assertTrue("the exported file should decode as a backup, got $decoded", decoded is BackupCodec.DecodeResult.Ok)
        assertEquals(book, (decoded as BackupCodec.DecodeResult.Ok).quotes)
    }
}
