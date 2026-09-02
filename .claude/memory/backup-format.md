# Backup, restore, and storage

Backup was the actual point of the request, so this is the part that gets the paranoia.

## The file format

```json
{
  "format": "commonplace.quotes",
  "version": 1,
  "quotes": [ { "id": "…", "text": "…", "author": "…" } ]
}
```

Pretty-printed on purpose — the user can open it in a text editor. The envelope exists so that
"this is not our file" is *detectable* rather than half-parsed out of a stranger's JSON.

`author` is optional. `id` is stable for the life of a quote, which is what makes restoring the
same file twice a no-op.

## Decoding never throws and never half-succeeds

[`BackupCodec.decode()`](../../logic/src/main/kotlin/app/commonplace/logic/BackupCodec.kt)
returns a sealed `DecodeResult`:

| Result | Means | User sees |
|---|---|---|
| `Ok(quotes)` | A genuine backup | The merge/replace prompt |
| `NotOurFormat` | Parsed, but not ours | "That is not a Commonplace backup" |
| `UnsupportedVersion(n)` | Ours, from a newer app | "That came from a newer version" |
| `Malformed(reason)` | Ours by intent, unreadable | The reason, verbatim |

Only `Ok` leads to a write. Everything else leaves the collection untouched.

**Gotchas already discovered and pinned by tests:**

- **A lone unquoted token parses as a primitive.** `<xml/>` does *not* fail JSON parsing — the
  parser reads it as a bare literal, so it comes back `NotOurFormat`, not `Malformed`. Do not
  "fix" this; the test documents it.
- **A UTF-8 BOM is not valid JSON.** Windows editors add one. `decode` strips a leading `U+FEFF` before parsing.
- **`ignoreUnknownKeys = true`** so a file written by a future version still restores here.
- **`MAX_BYTES` is 5 MB.** Both `QuoteStore` and the SAF import refuse anything larger, so
  picking the wrong file cannot exhaust memory.

## Merge versus replace

[`QuoteImport.apply()`](../../logic/src/main/kotlin/app/commonplace/logic/QuoteImport.kt) takes
the existing collection, the incoming one, and a mode.

- **REPLACE** — the backup becomes the whole collection.
- **MERGE** — adds only what is not already present, by `id` *or* by `Quote.fingerprint`
  (trimmed, whitespace-collapsed, lowercased text). This is what stops an old backup wiping
  quotes added since.

The prompt is only shown when the collection is non-empty; restoring onto a fresh install just
restores.

**Every incoming quote goes through `QuoteValidation.clean()`** — the same rule the editor uses.
There is deliberately no second filtering path.

## Counts, because silence is a bug

`ImportSummary` carries `imported`, `skippedInvalid`, `skippedDuplicate`, and
`totalInFile` — and a test asserts the three parts sum to the file's size, so nothing can vanish
unaccounted for. `MainActivity.applyImport` surfaces them: *"Restored 39 of 42 — 3 skipped."*

## On-disk storage

[`QuoteStore`](../../app/src/main/kotlin/app/commonplace/QuoteStore.kt) keeps `quotes.json` in
`filesDir`. Writing is deliberately paranoid:

1. Serialise **first** — if encoding failed, the stored file must be untouched.
2. Write to `quotes.json.tmp`, flush, `fd.sync()`.
3. Copy the current `quotes.json` to `quotes.json.bak`.
4. Rename the temp file over `quotes.json`.

Reading tries `quotes.json`, falls back to `quotes.json.bak`, then gives up with an empty list.
So a corrupted, truncated, or emptied main file costs you the most recent save — never the whole
collection.

**Never replace this with a plain `writeText`.** The whole promise of the app is in these steps.

## Export and import plumbing

Storage Access Framework, so **no storage permission** is needed:

- Export — `ActivityResultContracts.CreateDocument("application/json")`. The payload is built
  **entirely in memory first**, then written in one go, so a crash cannot leave a half-written
  backup in the user's chosen location.
- Import — `ActivityResultContracts.OpenDocument()` with a permissive type list, because some
  pickers hide `.json` behind the strict type. A file that is not a backup is rejected by the
  codec with a clear message, so being permissive costs nothing.
- Reading is capped by `readCapped()`, which stops at `MAX_BYTES` rather than trusting the size.

## Android's own backup

`quotes.json` and `quotes.json.bak` are registered in **both**
[`backup_rules.xml`](../../app/src/main/res/xml/backup_rules.xml) (pre-31) and
[`data_extraction_rules.xml`](../../app/src/main/res/xml/data_extraction_rules.xml) (31+, cloud
*and* device transfer). Both files are needed — the newer attribute does not cover older
devices.
