package app.commonplace

import android.content.Context
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.commonplace.logic.Quote
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * These run on a real Android emulator in CI, not on the JVM.
 *
 * Everything else in this project is proven with Robolectric, which is an *approximation* of
 * Android — and the places it approximates hardest are exactly the places this app can fail on
 * a real phone: resource and theme resolution when an activity is really created, and whether
 * a widget layout is genuinely inflatable as RemoteViews.
 *
 * Kept deliberately small. This is a smoke suite answering "would this crash on a real device",
 * not a second copy of the unit tests.
 */
@RunWith(AndroidJUnit4::class)
class AppSmokeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @After
    fun clearStoredQuotes() {
        QuoteStore(context).save(emptyList())
    }

    @Test
    fun theAppLaunchesAndReachesTheForeground() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun theAppLaunchesWithQuotesAlreadyStored() {
        QuoteStore(context).save(listOf(Quote("a", "Already here", "Ann")))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun theAppSurvivesBeingRecreated() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.recreate()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    /**
     * The check that only a real device can make.
     *
     * A launcher renders a widget by calling `RemoteViews.apply()` in *its own* process. Any
     * view or attribute the layout uses that RemoteViews does not support throws there — in
     * someone else's app, at tap time. Robolectric's shadow does not enforce that restriction,
     * so this is the only place the widget layout is genuinely validated.
     */
    @Test
    fun theWidgetLayoutIsGenuinelyInflatableAsRemoteViews() {
        val views = RemoteViews(context.packageName, R.layout.widget_quote)
        views.setTextViewText(R.id.widget_quote_text, "A quote worth keeping")
        views.setTextViewText(R.id.widget_quote_author, "Someone")

        val inflated = views.apply(context, FrameLayout(context))

        assertNotNull("the widget layout could not be inflated as RemoteViews", inflated)
        assertNotNull(inflated.findViewById<android.view.View>(R.id.widget_quote_text))
        assertNotNull(inflated.findViewById<android.view.View>(R.id.widget_root))
    }

    @Test
    fun theWidgetPreviewLayoutIsGenuinelyInflatableAsRemoteViews() {
        // Declared as previewLayout for Android 12+, so the system inflates it in the picker.
        val views = RemoteViews(context.packageName, R.layout.widget_preview)

        assertNotNull(views.apply(context, FrameLayout(context)))
    }

    @Test
    fun quotesSurviveARealWriteAndReadFromDisk() {
        val book = listOf(
            Quote("a", "Line one\nLine two", "Ann"),
            Quote("b", "Unicode holds up: 日本語 🌱", null),
        )

        QuoteStore(context).save(book)

        assertEquals(book, QuoteStore(context).load())
    }
}
