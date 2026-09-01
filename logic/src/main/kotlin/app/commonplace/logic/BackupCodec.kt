package app.commonplace.logic

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

@Serializable
private data class Envelope(
    val format: String,
    val version: Int,
    val quotes: List<Quote>,
)

/**
 * The backup file format: plain, readable JSON the owner can open in any text editor.
 *
 * Decoding never throws and never returns a half-read book. Every way a file can be wrong
 * has its own answer, so the caller can say something true to the user and — crucially —
 * leave the existing quotes untouched.
 */
object BackupCodec {

    const val FORMAT = "commonplace.quotes"
    const val VERSION = 1

    /** Refuse to read anything larger. A mis-picked file cannot exhaust memory. */
    const val MAX_BYTES = 5 * 1024 * 1024

    sealed interface DecodeResult {
        data class Ok(val quotes: List<Quote>) : DecodeResult

        /** Parsed fine, but it is someone else's JSON — not a Commonplace backup. */
        data object NotOurFormat : DecodeResult

        /** Ours, but written by a newer version of the app than this one. */
        data class UnsupportedVersion(val found: Int) : DecodeResult

        /** Ours by intent but unreadable. [reason] is safe to show to the user. */
        data class Malformed(val reason: String) : DecodeResult
    }

    private val json = Json {
        // A file written by a later version may carry fields this build doesn't know.
        // Ignoring them means an older app can still restore a newer backup.
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun encode(quotes: List<Quote>): String =
        json.encodeToString(Envelope.serializer(), Envelope(FORMAT, VERSION, quotes))

    fun decode(raw: String): DecodeResult {
        // Editors on Windows routinely prepend a byte-order mark, which is not valid JSON.
        val text = raw.removePrefix(BYTE_ORDER_MARK).trim()
        if (text.isEmpty()) return DecodeResult.Malformed("The file is empty.")

        val root = try {
            json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            return DecodeResult.Malformed("This file is not valid JSON.")
        }
        if (root !is JsonObject) return DecodeResult.NotOurFormat

        val format = (root[KEY_FORMAT] as? JsonPrimitive)?.contentOrNull
        if (format != FORMAT) return DecodeResult.NotOurFormat

        val version = (root[KEY_VERSION] as? JsonPrimitive)?.intOrNull
            ?: return DecodeResult.Malformed("The backup has no version number.")
        if (version > VERSION) return DecodeResult.UnsupportedVersion(version)

        return try {
            DecodeResult.Ok(json.decodeFromJsonElement(Envelope.serializer(), root).quotes)
        } catch (e: SerializationException) {
            DecodeResult.Malformed("The backup is missing information it needs.")
        }
    }

    private const val KEY_FORMAT = "format"
    private const val KEY_VERSION = "version"
    private const val BYTE_ORDER_MARK = "\uFEFF"
}
