package app.commonplace

import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import app.commonplace.databinding.ActivityMainBinding
import app.commonplace.databinding.DialogEditQuoteBinding
import app.commonplace.logic.BackupCodec
import app.commonplace.logic.ImportMode
import app.commonplace.logic.OrderMode
import app.commonplace.logic.Quote
import app.commonplace.logic.QuoteImport
import app.commonplace.logic.QuoteValidation
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var store: QuoteStore
    private lateinit var settings: WidgetSettings
    private lateinit var adapter: QuoteListAdapter

    private var quotes: List<Quote> = emptyList()

    private val chooseBackupDestination =
        registerForActivityResult(ActivityResultContracts.CreateDocument(JSON_MIME)) { uri ->
            uri?.let(::writeBackup)
        }

    private val chooseBackupToRestore =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(::readBackup)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = QuoteStore(this)
        settings = WidgetSettings(this)
        adapter = QuoteListAdapter(onEdit = ::showEditor, onDelete = ::confirmDelete)

        binding.quoteList.layoutManager = LinearLayoutManager(this)
        binding.quoteList.adapter = adapter
        binding.addQuote.setOnClickListener { showEditor(null) }
        binding.toolbar.setOnMenuItemClickListener(::onMenuSelected)

        show(store.load())
    }

    private fun onMenuSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_backup -> true.also { chooseBackupDestination.launch(DEFAULT_BACKUP_NAME) }
        R.id.action_restore -> true.also { chooseBackupToRestore.launch(OPENABLE_TYPES) }
        R.id.action_order -> true.also { showOrderPicker() }
        R.id.action_text_size -> true.also { showTextSizePicker() }
        else -> false
    }

    // --- the collection ---

    private fun show(updated: List<Quote>) {
        quotes = updated
        adapter.submitList(updated)
        binding.emptyState.visibility = if (updated.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun persist(updated: List<Quote>) {
        try {
            store.save(updated)
        } catch (e: IOException) {
            toast(getString(R.string.save_failed))
            return
        }
        show(updated)
        QuoteWidgetProvider.refreshAll(this)
    }

    private fun showEditor(existing: Quote?) {
        val fields = DialogEditQuoteBinding.inflate(layoutInflater)
        fields.inputText.setText(existing?.text)
        fields.inputAuthor.setText(existing?.author)

        MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) R.string.add_quote else R.string.edit_quote)
            .setView(fields.root)
            .setPositiveButton(R.string.save) { _, _ ->
                saveQuote(existing, fields.inputText.text.toString(), fields.inputAuthor.text.toString())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun saveQuote(existing: Quote?, text: String, author: String) {
        when (val result = QuoteValidation.validate(text, author)) {
            QuoteValidation.Result.Blank -> toast(getString(R.string.error_blank))

            is QuoteValidation.Result.TooLong ->
                toast(getString(R.string.error_too_long, QuoteValidation.MAX_TEXT_LENGTH))

            is QuoteValidation.Result.Valid -> {
                val edited = existing?.copy(text = result.text, author = result.author)
                    ?: Quote(UUID.randomUUID().toString(), result.text, result.author)
                persist(
                    if (existing == null) quotes + edited
                    else quotes.map { if (it.id == edited.id) edited else it },
                )
            }
        }
    }

    private fun confirmDelete(quote: Quote) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_quote)
            .setMessage(quote.text)
            .setPositiveButton(R.string.delete) { _, _ -> persist(quotes.filterNot { it.id == quote.id }) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // --- settings ---

    private fun showOrderPicker() {
        val modes = OrderMode.entries.toTypedArray()
        val labels = arrayOf(getString(R.string.order_sequential), getString(R.string.order_shuffle))

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_order)
            .setSingleChoiceItems(labels, modes.indexOf(settings.orderMode)) { dialog, which ->
                settings.orderMode = modes[which]
                QuoteWidgetProvider.refreshAll(this)
                dialog.dismiss()
            }
            .show()
    }

    private fun showTextSizePicker() {
        val labels = TEXT_SIZES.map { getString(R.string.text_size_option, it.toInt()) }.toTypedArray()
        val current = TEXT_SIZES.indexOfFirst { it == settings.textSizeSp }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_text_size)
            .setSingleChoiceItems(labels, current) { dialog, which ->
                settings.textSizeSp = TEXT_SIZES[which]
                QuoteWidgetProvider.refreshAll(this)
                dialog.dismiss()
            }
            .show()
    }

    // --- backup and restore ---

    private fun writeBackup(destination: Uri) {
        // Serialise everything first: a half-written file is worse than no file.
        val payload = BackupCodec.encode(quotes).toByteArray(Charsets.UTF_8)
        try {
            contentResolver.openOutputStream(destination, "wt")?.use { it.write(payload) }
                ?: return toast(getString(R.string.backup_failed))
        } catch (e: IOException) {
            return toast(getString(R.string.backup_failed))
        }
        toast(resources.getQuantityString(R.plurals.backup_saved, quotes.size, quotes.size))
    }

    private fun readBackup(source: Uri) {
        val raw = try {
            contentResolver.openInputStream(source)?.use(::readCapped)
        } catch (e: IOException) {
            null
        } ?: return toast(getString(R.string.restore_unreadable))

        when (val decoded = BackupCodec.decode(raw)) {
            is BackupCodec.DecodeResult.Ok -> chooseImportMode(decoded.quotes)
            BackupCodec.DecodeResult.NotOurFormat -> toast(getString(R.string.restore_not_ours))
            is BackupCodec.DecodeResult.UnsupportedVersion ->
                toast(getString(R.string.restore_newer_version))
            is BackupCodec.DecodeResult.Malformed ->
                toast(getString(R.string.restore_malformed, decoded.reason))
        }
    }

    /** Reads at most [BackupCodec.MAX_BYTES], so picking the wrong file cannot exhaust memory. */
    private fun readCapped(input: InputStream): String? {
        val collected = ByteArrayOutputStream()
        val chunk = ByteArray(READ_CHUNK_BYTES)
        while (true) {
            val read = input.read(chunk)
            if (read == -1) break
            if (collected.size() + read > BackupCodec.MAX_BYTES) return null
            collected.write(chunk, 0, read)
        }
        return collected.toString(Charsets.UTF_8.name())
    }

    private fun chooseImportMode(incoming: List<Quote>) {
        // Nothing to lose yet, so don't make the user answer a question about merging.
        if (quotes.isEmpty()) return applyImport(incoming, ImportMode.REPLACE)

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.restore_title)
            .setMessage(getString(R.string.restore_message, incoming.size, quotes.size))
            .setPositiveButton(R.string.restore_merge) { _, _ -> applyImport(incoming, ImportMode.MERGE) }
            .setNegativeButton(R.string.restore_replace) { _, _ -> applyImport(incoming, ImportMode.REPLACE) }
            .setNeutralButton(R.string.cancel, null)
            .show()
    }

    private fun applyImport(incoming: List<Quote>, mode: ImportMode) {
        val summary = QuoteImport.apply(quotes, incoming, mode)
        persist(summary.quotes)

        val skipped = summary.skippedInvalid + summary.skippedDuplicate
        toast(
            if (skipped == 0) getString(R.string.restore_done, summary.imported)
            else getString(R.string.restore_done_with_skips, summary.imported, summary.totalInFile, skipped),
        )
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private companion object {
        const val JSON_MIME = "application/json"
        const val DEFAULT_BACKUP_NAME = "commonplace-quotes.json"
        const val READ_CHUNK_BYTES = 8 * 1024

        // Some file pickers hide .json behind the JSON type alone, so allow anything;
        // a file that is not a backup is rejected by the codec with a clear message.
        val OPENABLE_TYPES = arrayOf(JSON_MIME, "text/plain", "*/*")
        val TEXT_SIZES = listOf(12f, 14f, 16f, 20f, 24f, 28f)
    }
}
