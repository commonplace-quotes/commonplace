package app.commonplace

import android.content.Context
import app.commonplace.logic.OrderMode
import app.commonplace.logic.Rotation

/**
 * The small amount of state that is not the quotes themselves.
 *
 * Order and text size are app-wide. Only the cursor — which quote a particular widget is
 * showing — is per-widget, and it is cleared when that widget is removed so the preferences
 * cannot grow without bound.
 */
class WidgetSettings(context: Context) {

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var orderMode: OrderMode
        get() = runCatching { OrderMode.valueOf(prefs.getString(KEY_ORDER, null) ?: "") }
            .getOrDefault(OrderMode.SEQUENTIAL)
        set(value) = prefs.edit().putString(KEY_ORDER, value.name).apply()

    var textSizeSp: Float
        get() = prefs.getFloat(KEY_TEXT_SIZE, DEFAULT_TEXT_SIZE_SP)
        set(value) = prefs.edit().putFloat(KEY_TEXT_SIZE, value.coerceIn(MIN_TEXT_SIZE_SP, MAX_TEXT_SIZE_SP)).apply()

    fun cursorFor(widgetId: Int): Int = prefs.getInt(cursorKey(widgetId), Rotation.NO_QUOTE)

    fun setCursor(widgetId: Int, index: Int) {
        prefs.edit().putInt(cursorKey(widgetId), index).apply()
    }

    fun forgetWidget(widgetId: Int) {
        prefs.edit().remove(cursorKey(widgetId)).apply()
    }

    private fun cursorKey(widgetId: Int) = "$KEY_CURSOR_PREFIX$widgetId"

    companion object {
        const val DEFAULT_TEXT_SIZE_SP = 16f
        const val MIN_TEXT_SIZE_SP = 10f
        const val MAX_TEXT_SIZE_SP = 32f

        private const val NAME = "commonplace"
        private const val KEY_ORDER = "order_mode"
        private const val KEY_TEXT_SIZE = "text_size_sp"
        private const val KEY_CURSOR_PREFIX = "cursor_"
    }
}
