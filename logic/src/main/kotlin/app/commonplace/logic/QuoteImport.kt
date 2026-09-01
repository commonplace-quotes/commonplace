package app.commonplace.logic

enum class ImportMode {
    /** The backup becomes the whole collection. */
    REPLACE,

    /** The backup is added to what is already there; nothing existing is removed. */
    MERGE,
}

/**
 * What a restore actually did. The counts exist so the app can never silently drop a
 * quote — the user is told "restored 39 of 42" rather than quietly getting 39.
 */
data class ImportSummary(
    val quotes: List<Quote>,
    val imported: Int,
    val skippedInvalid: Int,
    val skippedDuplicate: Int,
) {
    val totalInFile: Int get() = imported + skippedInvalid + skippedDuplicate
}

object QuoteImport {

    fun apply(existing: List<Quote>, incoming: List<Quote>, mode: ImportMode): ImportSummary {
        val kept: MutableList<Quote> = when (mode) {
            ImportMode.REPLACE -> mutableListOf()
            ImportMode.MERGE -> existing.toMutableList()
        }
        val seenIds = kept.mapTo(mutableSetOf()) { it.id }
        val seenFingerprints = kept.mapTo(mutableSetOf()) { it.fingerprint }

        var imported = 0
        var skippedInvalid = 0
        var skippedDuplicate = 0

        for (candidate in incoming) {
            // The same rule the editor applies, so a hand-typed quote and a restored one
            // are held to one standard.
            val quote = QuoteValidation.clean(candidate)
            if (quote == null) {
                skippedInvalid++
                continue
            }
            val isNewId = seenIds.add(quote.id)
            val isNewText = seenFingerprints.add(quote.fingerprint)
            if (!isNewId || !isNewText) {
                skippedDuplicate++
                continue
            }
            kept += quote
            imported++
        }

        return ImportSummary(kept, imported, skippedInvalid, skippedDuplicate)
    }
}
