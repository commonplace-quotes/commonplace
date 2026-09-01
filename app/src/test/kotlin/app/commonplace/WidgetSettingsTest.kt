package app.commonplace

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.commonplace.logic.OrderMode
import app.commonplace.logic.Rotation
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetSettingsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val settings: WidgetSettings get() = WidgetSettings(context)

    @Test
    fun `order defaults to sequential, so every quote is reachable`() {
        assertEquals(OrderMode.SEQUENTIAL, settings.orderMode)
    }

    @Test
    fun `order survives being written and read back`() {
        settings.orderMode = OrderMode.SHUFFLE
        assertEquals(OrderMode.SHUFFLE, settings.orderMode)
    }

    @Test
    fun `an unrecognised stored order falls back to sequential instead of crashing`() {
        context.getSharedPreferences("commonplace", Context.MODE_PRIVATE)
            .edit()
            .putString("order_mode", "NOT_A_REAL_MODE")
            .commit()

        assertEquals(OrderMode.SEQUENTIAL, settings.orderMode)
    }

    @Test
    fun `text size has a readable default`() {
        assertEquals(WidgetSettings.DEFAULT_TEXT_SIZE_SP, settings.textSizeSp, 0.01f)
    }

    @Test
    fun `text size is clamped to a usable range`() {
        settings.textSizeSp = 200f
        assertEquals(WidgetSettings.MAX_TEXT_SIZE_SP, settings.textSizeSp, 0.01f)

        settings.textSizeSp = 1f
        assertEquals(WidgetSettings.MIN_TEXT_SIZE_SP, settings.textSizeSp, 0.01f)
    }

    @Test
    fun `a widget with no cursor yet reports no quote`() {
        assertEquals(Rotation.NO_QUOTE, settings.cursorFor(99))
    }

    @Test
    fun `each widget keeps its own cursor`() {
        settings.setCursor(1, 5)
        settings.setCursor(2, 9)

        assertEquals(5, settings.cursorFor(1))
        assertEquals(9, settings.cursorFor(2))
    }

    @Test
    fun `forgetting a widget clears only that widget`() {
        settings.setCursor(1, 5)
        settings.setCursor(2, 9)

        settings.forgetWidget(1)

        assertEquals(Rotation.NO_QUOTE, settings.cursorFor(1))
        assertEquals(9, settings.cursorFor(2))
    }
}
