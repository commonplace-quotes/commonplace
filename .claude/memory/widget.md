# The home-screen widget

`QuoteWidgetProvider` is the whole widget. It holds no state: what it shows is derived from the
stored collection plus this widget's cursor, so there is no second copy of the truth to drift.

## The PendingIntent trap — read before touching tap handling

`PendingIntent` decides whether it can reuse an existing intent with `Intent.filterEquals()`,
which compares **action, data, type, package, component and categories — and ignores extras.**

Two widgets whose intents differ only by the widget-id extra are therefore *equal* as far as the
system is concerned. Both get one shared `PendingIntent`, and tapping either advances whichever
was registered last. The symptom is "tapping one widget changes the other one".

The fix, in `nextIntent()`:

```kotlin
Intent(context, QuoteWidgetProvider::class.java).apply {
    action = ACTION_NEXT
    data = Uri.parse("commonplace://widget/$widgetId")   // <- this is load-bearing
    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
}
```

Plus `widgetId` as the request code and `FLAG_IMMUTABLE` (mandatory from API 31).

**Never remove the `data` Uri.** Locked by `QuoteWidgetProviderTest.two widgets produce intents
the system can tell apart`, with a companion test asserting a single widget *does* reuse its own.

## How a tap flows

1. Launcher fires the `PendingIntent` → `onReceive` with `ACTION_NEXT`.
2. Widget id read from the extra; an intent without one is ignored, not a crash.
3. `Rotation.next(count, cursor, mode, random)` picks the next index.
4. New cursor saved to SharedPreferences, then `render()` redraws that widget only.

`onReceive` returns early for `ACTION_NEXT` and delegates everything else to `super`, which is
what dispatches `onUpdate` / `onDeleted`.

## Rendering

`render()` asks [`WidgetQuote.select()`](../../logic/src/main/kotlin/app/commonplace/logic/WidgetQuote.kt)
what to display — a pure function, which is why widget *content* is testable without fighting
`ShadowRemoteViews`. It returns `Shown(text, author)` or `Empty`.

- Author line is hidden with `View.GONE` when absent rather than left blank.
- Text size comes from app-wide settings via `setTextViewTextSize`.
- The whole root view is the tap target, not a button inside it.

## Per-widget state

Only the **cursor** is per-widget (`cursor_<widgetId>` in SharedPreferences). Order mode and text
size are app-wide.

**`onDeleted` must clear the cursor key** or preferences grow unbounded as widgets are added and
removed. Tested both ways: removing a widget forgets its cursor, and leaves other widgets alone.

## Refreshing after a change

`QuoteWidgetProvider.refreshAll(context)` re-renders every placed widget. Call it after *any*
change to the collection or to settings — `MainActivity.persist()` does this on every write.

## Widget metadata

Two `appwidget-provider` files: [`res/xml`](../../app/src/main/res/xml/quote_widget_info.xml) for
the base case and [`res/xml-v31`](../../app/src/main/res/xml-v31/quote_widget_info.xml) adding
`previewLayout` and `targetCell*`, which are API 31+ only. Keep them in sync when changing size
or resize behaviour.

## Deliberately absent

**There is no widget configuration activity.** It was designed and then cut: the only genuinely
per-widget value is the cursor, and a config activity adds the well-known "widget placed, config
cancelled, dead icon on the home screen" failure. Settings live in the app instead.
