# Testing and CI

## There is no local Android toolchain

The normal workflow for this project has **no JDK compiler, no Android SDK, no Gradle, and no
device**. GitHub Actions is the test runner. Push the branch, read the run.

This is *why* the module split exists: `:logic` is plain Kotlin, so its rules are provable in
seconds on any JDK, and `:app` is kept thin enough that Robolectric can cover it.

Practical consequence: **budget a CI round-trip (~3.5 min) for every verification**. Batch fixes
rather than pushing one at a time.

## The two suites

| Module | Framework | Count | What it covers |
|---|---|---|---|
| `:logic` | JUnit 5 | 117 | Rotation, validation, backup format, import semantics, fingerprinting |
| `:app` | JUnit 4 + Robolectric | 50 | Widget intents and cursors, file storage and recovery, settings, manifest, the main screen end to end |

Robolectric is pinned to **SDK 34** in
[`robolectric.properties`](../../app/src/test/resources/robolectric.properties).

## The zero-test guard — do not weaken it

Gradle's `test` task **passes trivially when it finds nothing to run**, so a green check can be
completely hollow. [`ci.yml`](../../.github/workflows/ci.yml) counts `<testcase` entries in the
XML results for both modules and fails if either is zero.

If a refactor moves test sources and the count silently drops to zero, this step is the only
thing standing between that and a "passing" build.

`upload-artifact` uses `if-no-files-found: error` for the same reason — a missing APK must fail
loudly rather than warn.

## Testing the widget without a device

`ShadowAppWidgetManager.createWidgets(provider, layoutId, count)` registers real widget ids.
**This matters:** `updateAppWidget` on an unregistered id throws inside the shadow, so a test
that skips `createWidgets` and calls `onReceive` directly will fail confusingly.

The pattern used throughout `QuoteWidgetProviderTest`:

```kotlin
val id = createWidgets(1).first()
WidgetSettings(context).setCursor(id, 0)
QuoteWidgetProvider().onReceive(context, QuoteWidgetProvider.nextIntent(context, id))
// then assert on the cursor, not on rendered RemoteViews
```

**Assert on state transitions, not on rendered `RemoteViews`.** `ShadowRemoteViews` is thin and
asserting rendered text is brittle — which is exactly why "which quote does this cursor show"
was extracted into the pure `WidgetQuote.select()` in `:logic`.

## Driving the activity

`MainActivityTest` stands the real activity up with `Robolectric.buildActivity(...).setup()` and
drives it through the actual views — tap the button, fill the dialog, press save.

**`shadowOf(Looper.getMainLooper()).idle()` after a dialog button click is load-bearing.**
Robolectric runs the main looper paused, so `performClick()` only *queues* the listener. Reading
the store straight afterwards sees the state from before the save, which looks exactly like "the
app doesn't save" — it cost a full debugging round to work out the app was fine and the test was
not. Every interaction that changes state must drain the looper before asserting.

## Things learned the hard way

- **`assertFalse` argument order differs between JUnit 4 and 5.** JUnit 5 is
  `assertFalse(condition, message)`; JUnit 4 is `assertFalse(message, condition)`. Both suites
  exist in this repo, so check which module you are in.
- **The merged manifest is never permission-free.** AndroidX injects
  `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Asserting an empty list fails;
  `ManifestTest` asserts the rule that matters instead — no platform, foreign, `INTERNET`,
  storage, or package-listing permission.
- **Prefer testing the invariant over the implementation detail.** `BackupCodecTest` asserts
  "junk never decodes to an importable collection" across fifteen hostile inputs, alongside the
  specific per-branch cases. The invariant survives parser quirks; the specific case did not.

## Style

Test names are backtick sentences describing the expectation. Parameterized tests
(`@ValueSource`) carry a `name = "…[{0}]"` so a failure names the offending input rather than an
index. Assertions carry a message when the failure would otherwise be a bare `expected:<[]>`.
