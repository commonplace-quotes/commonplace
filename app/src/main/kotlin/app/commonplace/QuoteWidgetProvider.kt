package app.commonplace

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import app.commonplace.logic.Rotation
import app.commonplace.logic.WidgetQuote
import kotlin.random.Random

/**
 * The home-screen widget: one quote, and a tap anywhere on it moves to the next.
 *
 * The widget holds no state of its own. What it shows is derived from the stored collection
 * and this widget's cursor, so there is no second copy of the truth that can drift.
 */
class QuoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { render(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_NEXT) {
            val widgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                advance(context, widgetId)
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val settings = WidgetSettings(context)
        appWidgetIds.forEach { settings.forgetWidget(it) }
    }

    private fun advance(context: Context, widgetId: Int) {
        val book = QuoteStore(context).load()
        val settings = WidgetSettings(context)
        val next = Rotation.next(book.size, settings.cursorFor(widgetId), settings.orderMode, Random.Default)

        settings.setCursor(widgetId, next)
        render(context, AppWidgetManager.getInstance(context), widgetId)
    }

    companion object {

        const val ACTION_NEXT = "app.commonplace.action.NEXT_QUOTE"

        /**
         * The intent a tap on [widgetId] fires.
         *
         * The `data` Uri is not decoration. `PendingIntent` decides whether an existing
         * intent can be reused with [Intent.filterEquals], which compares action, data,
         * type, package, component and categories — **but not extras**. Two widgets whose
         * intents differed only by the widget-id extra would therefore share a single
         * PendingIntent, and tapping one would advance the other. The per-widget Uri is
         * what keeps them distinct. Locked by QuoteWidgetProviderTest.
         */
        fun nextIntent(context: Context, widgetId: Int): Intent =
            Intent(context, QuoteWidgetProvider::class.java).apply {
                action = ACTION_NEXT
                data = Uri.parse("commonplace://widget/$widgetId")
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }

        /** Re-draws every placed widget. Called whenever the collection or settings change. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, QuoteWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { render(context, manager, it) }
        }

        private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val settings = WidgetSettings(context)
            val display = WidgetQuote.select(QuoteStore(context).load(), settings.cursorFor(widgetId))
            val views = RemoteViews(context.packageName, R.layout.widget_quote)

            when (display) {
                is WidgetQuote.Display.Shown -> {
                    views.setTextViewText(R.id.widget_quote_text, display.text)
                    views.setTextViewText(R.id.widget_quote_author, display.author.orEmpty())
                    views.setViewVisibility(
                        R.id.widget_quote_author,
                        if (display.author == null) View.GONE else View.VISIBLE,
                    )
                }

                WidgetQuote.Display.Empty -> {
                    views.setTextViewText(R.id.widget_quote_text, context.getString(R.string.widget_empty))
                    views.setViewVisibility(R.id.widget_quote_author, View.GONE)
                }
            }

            views.setTextViewTextSize(
                R.id.widget_quote_text,
                TypedValue.COMPLEX_UNIT_SP,
                settings.textSizeSp,
            )
            views.setOnClickPendingIntent(R.id.widget_root, tapIntent(context, widgetId))
            manager.updateAppWidget(widgetId, views)
        }

        private fun tapIntent(context: Context, widgetId: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                widgetId,
                nextIntent(context, widgetId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
