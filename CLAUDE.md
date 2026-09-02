# Commonplace — project rules

An Android home-screen widget for quotes the user typed in themselves, with backup and restore
to a file they own. Two Gradle modules: `:logic` (plain Kotlin, no Android) and `:app` (the
Android shell).

---

## The promises this app makes

These are the reasons the app exists. Breaking one is a defect, not a design change.

- **NEVER add a permission.** No `INTERNET`, no storage, no `QUERY_ALL_PACKAGES`. The README
  states this and [`ManifestTest`](app/src/test/kotlin/app/commonplace/ManifestTest.kt) enforces
  it. The only entry in the merged manifest is AndroidX's app-private
  `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, which grants nothing.
- **NEVER let a restore destroy quotes.** Validate the whole file before writing anything; on
  any failure leave the stored collection untouched. See
  [backup-format.md](.claude/memory/backup-format.md).
- **NEVER drop a quote silently.** Import returns counts and the UI shows them. A filtered-out
  quote the user is not told about is a bug.
- **NEVER log quote text.** It is personal content.
- **ALWAYS keep the `.bak` copy path working.** Atomic write, previous copy retained, recovery
  on read. It is the difference between "a backup app" and "an app that lost my backup".

## Architecture rules

- **ALWAYS put a rule in `:logic`, not `:app`.** If it can be expressed without an Android type,
  it belongs in `:logic` where it is testable on a plain JDK. `:app` should be a thin
  translation layer. This is the single most important structural rule here.
- **NEVER add an Android dependency to `:logic`.** That module's whole value is that it runs
  without an emulator.
- **`:logic` exposes kotlinx-serialization with `api()`, not `implementation()`** — `:app`
  references the `@Serializable` model types, so the annotations must reach its compile
  classpath.
- **Widget state lives in exactly two places:** the quote collection (`quotes.json`) and each
  widget's cursor (SharedPreferences). NEVER cache a third copy — "which icon" and "which quote"
  must be the same question.

## The trap that will bite you

**`Intent.filterEquals()` ignores extras.** `PendingIntent` uses it to decide whether an
existing intent can be reused. Two widgets whose intents differ only by an extra therefore share
one `PendingIntent`, and tapping one advances the other.

`QuoteWidgetProvider.nextIntent()` gives every widget its own `data` Uri
(`commonplace://widget/<id>`) as well as its own request code. **NEVER remove that Uri**, and
NEVER assume the request code alone is enough. Locked by
[`QuoteWidgetProviderTest`](app/src/test/kotlin/app/commonplace/QuoteWidgetProviderTest.kt) —
see [widget.md](.claude/memory/widget.md).

## Build and test

There is **no local Android toolchain and no device** in this project's normal workflow — CI is
the test runner. Push the branch and read the run.

```bash
gradle :logic:test             # the rules, any JDK
gradle :app:testDebugUnitTest  # the Android layer, via Robolectric
gradle :app:assembleDebug      # the APK
```

- **NEVER trust a green `test` task alone.** Gradle passes trivially when it finds no tests, so
  [`ci.yml`](.github/workflows/ci.yml) counts `<testcase` entries and fails at zero. Do not
  weaken that step.
- **No Gradle wrapper is committed.** Requires Gradle 8.10.2, JDK 17, Android SDK 35.
- Robolectric is pinned to SDK 34 via
  [`robolectric.properties`](app/src/test/resources/robolectric.properties).

## Publishing rules

This repo is published anonymously and lives under its own org, not a personal account.

- **NEVER commit with a personal git identity.** The repo-local identity is
  `commonplace <commonplace@noreply.invalid>`. Check `git log --format='%an <%ae>'` before any
  push — owner-level anonymity is not commit-level anonymity.
- **NEVER open a pull request.** A PR permanently pins the author's account and GitHub provides
  no way to delete one. Commit to a branch and merge locally.
- **NEVER `push --tags`.** Local tooling tags branches; those tags must not travel.
- **ALWAYS delete workflow runs after confirming green.** The Actions tab publicly records the
  account that pushed. Let the run finish, verify, download any artifact you need, then
  `gh api -X DELETE /repos/{owner}/{repo}/actions/runs/{id}`.

## Conventions

- **ALWAYS put user-facing strings in [`strings.xml`](app/src/main/res/values/strings.xml).**
  Never hardcode them in Kotlin.
- **ALWAYS give icon-only controls a `contentDescription`.**
- Test names are backtick sentences describing the expectation, not the method under test.
- Sealed interfaces for results (`DecodeResult`, `Result`, `Display`) — never throw across a
  module boundary for an expected failure.

## Documentation

Tiered system: CLAUDE.md → [MEMORY.md](.claude/memory/MEMORY.md) → topic files (`.claude/memory/*.md`) → sub-topic files. Max 2 hops from cold start.

**Placement rule**: Prevents mistakes on ANY task → CLAUDE.md. Spans features → MEMORY.md. One feature → topic file. Narrow subtopic → sub-topic file.

**Updating docs**: When code changes affect a rule in CLAUDE.md, update CLAUDE.md. When code changes affect a feature covered by a memory file, update that file. Topic files target 40-150 lines — split into hub + sub-topic files when content clusters into distinct concerns.
