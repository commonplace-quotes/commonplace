# Commonplace

**Your own quotes on the home screen. Tap for the next one. A backup that cannot lose them.**

Built for a request on r/androidapps, where someone asked for a widget of their own quotes that
changes on tap — and, in their words, *"the most important: backup option for those quotes."*

> "An app which lets me set a widget with custom added quotes that change when tapped, and the
> most important: backup option for those quotes. I've been looking for something like this for
> a long time."

A *commonplace book* is the centuries-old name for the notebook where you copy down the lines
worth keeping. That is all this is, on a home screen.

---

## What it does

| | |
|---|---|
| **Add your own quotes** | Type them in, with an optional author. Nothing is pre-loaded and nothing is downloaded. |
| **Tap to advance** | The whole widget face is the button. In order, or shuffled. |
| **Back up to a file you own** | Plain readable JSON, saved wherever you choose. |
| **Restore without losing anything** | Restoring onto a device that already has quotes offers *keep both* or *replace*. |
| **Resize it** | Any size your launcher allows, with a choice of text size. |

## The backup, since that was the point

The request was really about backup, so that got the attention.

**It is a file you own.** Export goes through Android's own file picker, so it lands wherever
you put it — your SD card, Drive, anywhere. It is plain JSON you can open in a text editor:

```json
{
  "format": "commonplace.quotes",
  "version": 1,
  "quotes": [
    { "id": "…", "text": "The quote itself", "author": "Someone" }
  ]
}
```

**Restoring cannot silently eat your collection.** Four things stand between a bad file and
your quotes:

1. **Nothing is written until the whole file has been read and understood.** A file that is
   empty, truncated, not JSON, or somebody else's JSON is rejected with a plain-English reason
   and your quotes are left exactly as they were.
2. **"Keep both" mode.** If you already have quotes, restoring asks whether to merge or
   replace. Merge adds only what is genuinely new — matching on the words, ignoring case and
   spacing — so restoring an old backup can't wipe what you added since. Restoring the same
   file twice does nothing the second time.
3. **You are told the numbers.** "Restored 39 of 42 — 3 skipped as blank or already saved."
   Never a silent drop.
4. **The previous copy is kept.** Every save writes to a temporary file first and only then
   replaces the real one, keeping the last good version alongside. If the main file is ever
   corrupted, the app falls back to that copy instead of starting empty.

**It is also in your device backup.** `quotes.json` is registered with both Android's cloud
backup and its device-to-device transfer, so a new phone can bring the quotes across on its own.

## Permissions

**None that do anything.** No internet permission, so the app cannot phone home — that is
enforced by a test, not a promise. No storage permission either; the file picker grants access
to the one file you pick.

Being precise: the merged manifest does contain
`app.commonplace.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. That is injected by AndroidX for
its own non-exported receiver on Android 13+. It is signature-level, scoped to this app's own
package, grants access to nothing, and is never shown to you. `ManifestTest` pins the rule that
matters: no platform permission, no third-party permission, no `INTERNET`, no storage, no
package listing — ever.

## Getting it

**There is no prebuilt APK to download here, and the Actions tab is empty on purpose.** This
repo is published anonymously, and a workflow run permanently records the GitHub account that
triggered it — so runs are verified green and then deleted, which removes their artifacts too.

Fork it and the build is yours for free: [`.github/workflows/ci.yml`](.github/workflows/ci.yml)
runs both test suites and uploads the APK, and on your own fork those runs are yours to keep.

Or build it locally:

```bash
gradle :logic:test             # the rules, on any JDK
gradle :app:testDebugUnitTest  # the Android layer, via Robolectric
gradle :app:assembleDebug      # the APK
```

You need **JDK 17**, the **Android SDK (platform 35)**, and **Gradle 8.10.2**. There is no
Gradle wrapper committed; opening the project in Android Studio will offer to generate one.

A debug build is signed with the shared Android debug key, so installing it by hand will show
Play Protect's "unsafe app blocked" screen. *More details → Install anyway* gets past it.

## How it's built

Two modules, split so the part that matters is testable without a phone:

- **[`:logic`](logic/)** — plain Kotlin, **zero Android dependencies**. Every rule about which
  quote comes next, what counts as a valid quote, and what the backup format means.
- **[`:app`](app/)** — the Android shell: one activity, one widget provider, file storage.

That split is not decoration. It means the logic worth trusting runs on any JDK in about a
second, and the Android layer only has to be a thin translation.

### The subtle bit

Two widgets on one home screen must advance independently. That is harder than it sounds,
because `PendingIntent` decides whether it can reuse an existing intent using
`Intent.filterEquals()` — which compares action, data, type, component and categories, and
**deliberately ignores extras**. Two widgets whose intents differ only by a widget-id extra
would therefore share a single `PendingIntent`, and tapping one would advance the other.

So each widget's intent carries its own `data` Uri (`commonplace://widget/<id>`) as well as its
own request code. There is a dedicated regression test asserting the two intents are not
`filterEquals`.

## Status

**167 tests, all green** — 117 in `:logic` on the JVM, 50 in `:app` through Robolectric — plus a
debug APK that builds. The workflow refuses to report success if either module executes zero
tests, because Gradle's `test` task passes trivially when it finds nothing to run.

Covered: rotation (including shuffle never repeating the quote already showing, and single-quote
and empty collections), validation, the backup format against fifteen kinds of malformed input,
merge and replace semantics, atomic writes and recovery from a corrupted file, and the
two-widget independence described above.

The main screen is driven as a person would drive it, too — the activity is stood up for real,
a quote is typed into the actual dialog and saved, a blank one is refused, the app is closed and
reopened to prove the quote persisted, and the menu is exercised.

**It has never run on physical hardware.** There is no Android device or emulator in the
loop — everything above is proven by automated tests. The launcher's own drawing of the widget
and the system file picker are the two things tests cannot stand in for. If you install it and
something misbehaves, please open an issue with your launcher and Android version.

## Built with

Planned, red-teamed, written, tested and documented with **Omniscio**.

## Licence

MIT — see [LICENSE](LICENSE).
