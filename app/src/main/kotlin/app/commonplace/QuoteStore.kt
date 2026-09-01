package app.commonplace

import android.content.Context
import app.commonplace.logic.BackupCodec
import app.commonplace.logic.Quote
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Where the quotes actually live: one small JSON document in the app's private storage.
 *
 * The point of this app is that a collection cannot be lost, so writing is deliberately
 * paranoid — the new content goes to a temporary file, the previous good copy is kept as a
 * `.bak`, and only then does the temporary file replace the real one. A crash at any point
 * leaves either the old collection or the new one, never a half-written file.
 */
class QuoteStore(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)
    private val backup = File(context.filesDir, BACKUP_NAME)
    private val lock = Any()

    fun load(): List<Quote> = synchronized(lock) {
        readQuotes(file) ?: readQuotes(backup) ?: emptyList()
    }

    fun save(quotes: List<Quote>): Unit = synchronized(lock) {
        // Serialise before touching the disk: if encoding were to fail, the stored
        // collection must be exactly as it was.
        val encoded = BackupCodec.encode(quotes)
        val temp = File(file.parentFile, TEMP_NAME)

        FileOutputStream(temp).use { out ->
            out.write(encoded.toByteArray(Charsets.UTF_8))
            out.flush()
            out.fd.sync()
        }

        if (file.exists()) {
            file.copyTo(backup, overwrite = true)
        }
        if (!temp.renameTo(file)) {
            temp.delete()
            throw IOException("Could not replace $FILE_NAME")
        }
    }

    private fun readQuotes(source: File): List<Quote>? {
        if (!source.exists()) return null
        // A file this large is not a quote collection; refuse it rather than load it.
        if (source.length() > BackupCodec.MAX_BYTES) return null

        val raw = try {
            source.readText(Charsets.UTF_8)
        } catch (e: IOException) {
            return null
        }
        return (BackupCodec.decode(raw) as? BackupCodec.DecodeResult.Ok)?.quotes
    }

    private companion object {
        const val FILE_NAME = "quotes.json"
        const val BACKUP_NAME = "quotes.json.bak"
        const val TEMP_NAME = "quotes.json.tmp"
    }
}
