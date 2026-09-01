package app.commonplace

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.commonplace.logic.OrderMode
import app.commonplace.logic.Quote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class QuoteWidgetProviderTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val book = listOf(
        Quote("a", "First", null),
        Quote("b", "Second", null),
        Quote("c", "Third", null),
    )

    @Before
    fun setUp() {
        QuoteStore(context).save(book)
        WidgetSettings(context).orderMode = OrderMode.SEQUENTIAL
    }

    /**
     * The regression this whole class exists for.
     *
     * PendingIntent reuse is decided by [android.content.Intent.filterEquals], which compares
     * action, data, type, package, component and categories — and deliberately ignores extras.
     * If the two intents below were filter-equal, both widgets would end up sharing one
     * PendingIntent and tapping either would advance whichever was registered last.
     */
    @Test
    fun `two widgets produce intents the system can tell apart`() {
        val first = QuoteWidgetProvider.nextIntent(context, 1)
        val second = QuoteWidgetProvider.nextIntent(context, 2)

        assertFalse(
            "intents for different widgets must not be filterEquals, or they share a PendingIntent",
            first.filterEquals(second),
        )
        assertNotEquals(first.data, second.data)
    }

    @Test
    fun `the intent for a given widget is stable`() {
        val once = QuoteWidgetProvider.nextIntent(context, 7)
        val again = QuoteWidgetProvider.nextIntent(context, 7)

        assertTrue("the same widget must reuse its own PendingIntent", once.filterEquals(again))
    }

    @Test
    fun `the intent carries the widget id the receiver reads`() {
        val intent = QuoteWidgetProvider.nextIntent(context, 42)

        assertEquals(QuoteWidgetProvider.ACTION_NEXT, intent.action)
        assertEquals(42, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
    }

    // --- the path a launcher actually drives ---

    @Test
    fun `a tap advances that widget to the next quote`() {
        val id = createWidgets(1).first()
        val settings = WidgetSettings(context)
        settings.setCursor(id, 0)

        tap(id)

        assertEquals(1, settings.cursorFor(id))
    }

    @Test
    fun `a tap on one widget leaves another widget alone`() {
        val (first, second) = createWidgets(2).let { it[0] to it[1] }
        val settings = WidgetSettings(context)
        settings.setCursor(first, 0)
        settings.setCursor(second, 0)

        tap(first)

        assertEquals("the tapped widget should advance", 1, settings.cursorFor(first))
        assertEquals("the untapped widget must not move", 0, settings.cursorFor(second))
    }

    @Test
    fun `tapping past the end wraps around`() {
        val id = createWidgets(1).first()
        val settings = WidgetSettings(context)
        settings.setCursor(id, book.lastIndex)

        tap(id)

        assertEquals(0, settings.cursorFor(id))
    }

    @Test
    fun `a tap with no quotes stored does not crash and parks the cursor`() {
        QuoteStore(context).save(emptyList())
        val id = createWidgets(1).first()

        tap(id)

        assertEquals(app.commonplace.logic.Rotation.NO_QUOTE, WidgetSettings(context).cursorFor(id))
    }

    @Test
    fun `an intent without a widget id is ignored rather than crashing`() {
        val id = createWidgets(1).first()
        val settings = WidgetSettings(context)
        settings.setCursor(id, 0)

        val malformed = android.content.Intent(context, QuoteWidgetProvider::class.java)
            .setAction(QuoteWidgetProvider.ACTION_NEXT)
        QuoteWidgetProvider().onReceive(context, malformed)

        assertEquals(0, settings.cursorFor(id))
    }

    // --- cleanup ---

    @Test
    fun `removing a widget forgets its cursor so preferences cannot grow forever`() {
        val id = createWidgets(1).first()
        val settings = WidgetSettings(context)
        settings.setCursor(id, 2)

        QuoteWidgetProvider().onDeleted(context, intArrayOf(id))

        assertEquals(app.commonplace.logic.Rotation.NO_QUOTE, settings.cursorFor(id))
    }

    @Test
    fun `removing one widget leaves the others intact`() {
        val (first, second) = createWidgets(2).let { it[0] to it[1] }
        val settings = WidgetSettings(context)
        settings.setCursor(first, 1)
        settings.setCursor(second, 2)

        QuoteWidgetProvider().onDeleted(context, intArrayOf(first))

        assertEquals(2, settings.cursorFor(second))
    }

    private fun tap(widgetId: Int) {
        QuoteWidgetProvider().onReceive(context, QuoteWidgetProvider.nextIntent(context, widgetId))
    }

    private fun createWidgets(count: Int): IntArray =
        shadowOf(AppWidgetManager.getInstance(context))
            .createWidgets(QuoteWidgetProvider::class.java, R.layout.widget_quote, count)
}
