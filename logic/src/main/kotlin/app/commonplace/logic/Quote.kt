package app.commonplace.logic

import kotlinx.serialization.Serializable

/**
 * One saved quote.
 *
 * [id] is stable for the life of the quote and is what a backup file carries, so restoring
 * a file twice cannot produce two copies of the same entry.
 */
@Serializable
data class Quote(
    val id: String,
    val text: String,
    val author: String? = null,
) {
    /**
     * The form used to decide whether two quotes are "the same" to a human: case and
     * surrounding/repeated whitespace are ignored, so "  Be kind. " and "be kind." collide.
     */
    val fingerprint: String
        get() = text.trim().replace(WHITESPACE, " ").lowercase()

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
