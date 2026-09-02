package app.commonplace

import android.app.Dialog
import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import app.commonplace.logic.OrderMode
import app.commonplace.logic.Quote
import app.commonplace.logic.QuoteValidation
import com.google.android.material.appbar.MaterialToolbar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast

/**
 * The app has never been run on a physical device, so these stand the real activity up on the
 * JVM and drive it the way a person would. They exist to catch the failures that would
 * otherwise only appear on someone's phone: a crash on launch, a broken view binding, an
 * adapter that never gets its data, a dialog that saves nothing.
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun launch() = Robolectric.buildActivity(MainActivity::class.java).setup()

    // --- the smoke test that matters most ---

    @Test
    fun `the app opens without crashing on a fresh install`() {
        val activity = launch().get()
        assertNotNull("the activity failed to start", activity)
        assertTrue("the activity should not be finishing straight away", !activity.isFinishing)
    }

    @Test
    fun `the app opens without crashing when quotes already exist`() {
        QuoteStore(context).save(listOf(Quote("a", "Already saved", "Ann")))

        val activity = launch().get()

        assertTrue(!activity.isFinishing)
    }

    // --- what the user actually sees ---

    @Test
    fun `an empty collection shows the invitation rather than a blank screen`() {
        val activity = launch().get()

        val empty = activity.findViewById<View>(R.id.emptyState)
        assertEquals(View.VISIBLE, empty.visibility)
    }

    @Test
    fun `stored quotes are handed to the list`() {
        QuoteStore(context).save(listOf(Quote("a", "First", null), Quote("b", "Second", null)))

        val activity = launch().get()

        val list = activity.findViewById<RecyclerView>(R.id.quoteList)
        assertEquals(2, list.adapter?.itemCount)
    }

    @Test
    fun `the empty message is hidden once a quote exists`() {
        QuoteStore(context).save(listOf(Quote("a", "Something", null)))

        val activity = launch().get()

        assertEquals(View.GONE, activity.findViewById<View>(R.id.emptyState).visibility)
    }

    // --- adding a quote, end to end through the real dialog ---

    @Test
    fun `adding a quote through the dialog stores it and updates the list`() {
        val activity = launch().get()

        addQuote(activity, "Be kind whenever possible.", author = "Someone")

        val stored = QuoteStore(context).load()
        assertEquals("stored=$stored toast=${ShadowToast.getTextOfLatestToast()}", 1, stored.size)
        assertEquals("Be kind whenever possible.", stored.first().text)
        assertEquals("Someone", stored.first().author)
        assertEquals(1, activity.findViewById<RecyclerView>(R.id.quoteList).adapter?.itemCount)
    }

    @Test
    fun `a blank quote is refused rather than stored`() {
        val activity = launch().get()

        addQuote(activity, "   ")

        assertEquals("whitespace is not a quote", 0, QuoteStore(context).load().size)
        assertNotNull("the user must be told why", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `cancelling the dialog stores nothing`() {
        val activity = launch().get()

        activity.findViewById<View>(R.id.addQuote).performClick()
        val dialog = latestDialog()
        dialog.findViewById<EditText>(R.id.inputText)!!.setText("Never meant to be saved")
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, QuoteStore(context).load().size)
    }

    @Test
    fun `a quote longer than the cap is refused`() {
        val activity = launch().get()

        addQuote(activity, "x".repeat(QuoteValidation.MAX_TEXT_LENGTH + 1))

        assertEquals(0, QuoteStore(context).load().size)
    }

    @Test
    fun `an added quote survives closing and reopening the app`() {
        addQuote(launch().get(), "Should still be here")

        val reopened = launch().get()

        assertEquals(1, reopened.findViewById<RecyclerView>(R.id.quoteList).adapter?.itemCount)
        assertEquals("Should still be here", QuoteStore(context).load().first().text)
    }

    @Test
    fun `two quotes added in a row both stick`() {
        val activity = launch().get()

        addQuote(activity, "First one")
        addQuote(activity, "Second one")

        assertEquals(2, QuoteStore(context).load().size)
        assertEquals(2, activity.findViewById<RecyclerView>(R.id.quoteList).adapter?.itemCount)
    }

    // --- the menu, which is the only route to backup and settings ---

    @Test
    fun `the order and text size menu actions open their pickers`() {
        val activity = launch().get()
        val toolbar = activity.findViewById<MaterialToolbar>(R.id.toolbar)

        for (id in listOf(R.id.action_order, R.id.action_text_size)) {
            assertTrue("menu item was not handled", toolbar.menu.performIdentifierAction(id, 0))
            assertNotNull("expected a picker dialog to open", ShadowDialog.getLatestDialog())
            latestDialog().dismiss()
        }
    }

    @Test
    fun `choosing shuffle is remembered`() {
        val activity = launch().get()
        val toolbar = activity.findViewById<MaterialToolbar>(R.id.toolbar)

        toolbar.menu.performIdentifierAction(R.id.action_order, 0)
        // The picker lists the OrderMode values in declaration order; index 1 is SHUFFLE.
        latestDialog().listView.performItemClick(null, 1, 1)

        assertEquals(OrderMode.SHUFFLE, WidgetSettings(context).orderMode)
    }

    /**
     * Drives the real add-quote flow: tap the button, fill the dialog, confirm.
     *
     * The `idle()` is load-bearing. Robolectric runs the main looper in paused mode, so the
     * dialog button's click listener is only *queued* by `performClick()` — reading the store
     * before draining the queue sees the state from before the save.
     */
    private fun addQuote(activity: MainActivity, text: String, author: String? = null) {
        activity.findViewById<View>(R.id.addQuote).performClick()
        val dialog = latestDialog()

        dialog.findViewById<EditText>(R.id.inputText)!!.setText(text)
        author?.let { dialog.findViewById<EditText>(R.id.inputAuthor)!!.setText(it) }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun latestDialog(): AlertDialog {
        val dialog: Dialog? = ShadowDialog.getLatestDialog()
        assertNotNull("expected a dialog to be showing", dialog)
        return dialog as AlertDialog
    }
}
