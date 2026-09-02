# Commonplace — memory index

An Android quotes widget with backup that cannot lose data. `:logic` holds every rule as plain
Kotlin; `:app` is a thin Android shell over it.

## Quick rules (the ones most easily broken)

- **A rule belongs in `:logic`** unless it genuinely needs an Android type.
- **Every widget's tap intent needs its own `data` Uri** — extras alone do not distinguish
  `PendingIntent`s. See [widget.md](widget.md).
- **Nothing is written until the whole import is validated.** See
  [backup-format.md](backup-format.md).
- **Import reports counts.** A quote filtered out without telling the user is a bug.
- **No permissions, ever.** Enforced by a test.
- **CI is the test runner** — there is no local Android toolchain. See [testing.md](testing.md).

## Topic files

| File | When to load |
|---|---|
| [widget.md](widget.md) | Touching the home-screen widget, tap handling, `RemoteViews`, `PendingIntent`, per-widget cursors, or widget cleanup |
| [backup-format.md](backup-format.md) | Changing the backup file format, import/export, validation, merge-vs-replace, or how quotes are stored on disk |
| [testing.md](testing.md) | Writing or fixing tests, understanding the CI workflow, or working out why a run went red |

## Cross-cutting patterns

- **Typed results, never exceptions, for expected failures.** `BackupCodec.DecodeResult`,
  `QuoteValidation.Result`, and `WidgetQuote.Display` are sealed. A caller must handle every
  branch, so "what happens when the file is rubbish" can't be forgotten.
- **Total functions over guarded ones.** `Rotation.next()` and `WidgetQuote.select()` define an
  answer for every input — empty collection, single quote, stale cursor — because the widget
  calls them on the main thread and must never throw.
- **A stale cursor restarts, it does not blank.** A cursor pointing past the end of a shrunken
  collection shows the first quote. Showing nothing would look like data loss to someone who
  just deleted a quote.
- **One validation path.** The editor and the importer both go through `QuoteValidation`, so a
  typed quote and a restored quote are held to one standard.
- **Duplicates are decided by `Quote.fingerprint`** — trimmed, whitespace-collapsed, lowercased
  text. Ignoring the author is deliberate: the words are what repeat.
- **Settings are app-wide; only the cursor is per-widget.** There is deliberately no widget
  configuration activity — it added a "widget placed, config cancelled, dead icon" failure mode
  for no gain.

## Current state

167 tests green (117 `:logic`, 50 `:app`); debug APK builds. **Never run on physical
hardware** — the launcher's rendering and the system file picker are the two things tests
cannot stand in for. README states this plainly.
