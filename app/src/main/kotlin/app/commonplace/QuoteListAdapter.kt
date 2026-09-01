package app.commonplace

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.commonplace.databinding.ItemQuoteBinding
import app.commonplace.logic.Quote

class QuoteListAdapter(
    private val onEdit: (Quote) -> Unit,
    private val onDelete: (Quote) -> Unit,
) : ListAdapter<Quote, QuoteListAdapter.QuoteHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuoteHolder {
        val binding = ItemQuoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return QuoteHolder(binding)
    }

    override fun onBindViewHolder(holder: QuoteHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class QuoteHolder(private val binding: ItemQuoteBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(quote: Quote) {
            binding.quoteText.text = quote.text
            binding.quoteAuthor.text = quote.author.orEmpty()
            binding.quoteAuthor.visibility = if (quote.author == null) View.GONE else View.VISIBLE
            binding.root.setOnClickListener { onEdit(quote) }
            binding.deleteButton.setOnClickListener { onDelete(quote) }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Quote>() {
            override fun areItemsTheSame(oldItem: Quote, newItem: Quote) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Quote, newItem: Quote) = oldItem == newItem
        }
    }
}
