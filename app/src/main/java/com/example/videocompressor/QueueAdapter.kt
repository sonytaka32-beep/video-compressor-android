package com.example.videocompressor

import android.net.Uri
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.videocompressor.databinding.ItemQueueBinding
import java.util.Locale

class QueueAdapter(
    private val onCancelOrRemoveClicked: (QueueItem) -> Unit,
    private val onOpenClicked: (Uri) -> Unit,
    private val onShareClicked: (Uri) -> Unit
) : ListAdapter<QueueItem, QueueAdapter.QueueViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val binding = ItemQueueBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return QueueViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class QueueViewHolder(
        private val binding: ItemQueueBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: QueueItem) {
            val context = binding.root.context
            val originalSizeFormatted = Formatter.formatShortFileSize(context, item.originalSizeBytes)
            binding.tvFileName.text = item.displayName

            when (val status = item.status) {
                is QueueItemStatus.Queued -> {
                    binding.tvSizeInfo.text = context.getString(
                        R.string.size_queued_format,
                        originalSizeFormatted
                    )
                    binding.tvStatusDetail.text = context.getString(R.string.status_queued)
                    binding.progressBar.visibility = View.GONE
                    binding.layoutCompletedActions.visibility = View.GONE
                    binding.btnCancelItem.visibility = View.VISIBLE
                    binding.btnCancelItem.text = context.getString(R.string.action_cancel)
                }

                is QueueItemStatus.Compressing -> {
                    binding.tvSizeInfo.text = context.getString(
                        R.string.size_queued_format,
                        originalSizeFormatted
                    )
                    val elapsedText = formatDuration(status.elapsedMs)
                    val etaText = if (status.etaMs >= 0L) formatDuration(status.etaMs) else "--:--"
                    binding.tvStatusDetail.text = context.getString(
                        R.string.status_compressing_format,
                        status.progressPercent,
                        elapsedText,
                        etaText
                    )
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressBar.progress = status.progressPercent
                    binding.layoutCompletedActions.visibility = View.GONE
                    binding.btnCancelItem.visibility = View.VISIBLE
                    binding.btnCancelItem.text = context.getString(R.string.action_cancel)
                }

                is QueueItemStatus.Completed -> {
                    val compressedSizeFormatted = Formatter.formatShortFileSize(
                        context,
                        status.compressedSizeBytes
                    )
                    val savedPercent = item.savedPercentage ?: 0
                    binding.tvSizeInfo.text = context.getString(
                        R.string.size_completed_format,
                        originalSizeFormatted,
                        compressedSizeFormatted,
                        savedPercent
                    )
                    binding.tvStatusDetail.text = context.getString(
                        R.string.status_completed_format,
                        formatDuration(status.elapsedMs)
                    )
                    binding.progressBar.visibility = View.GONE
                    binding.layoutCompletedActions.visibility = View.VISIBLE
                    binding.btnCancelItem.visibility = View.VISIBLE
                    binding.btnCancelItem.text = context.getString(R.string.action_remove)

                    val outputUri = Uri.parse(status.outputUriString)
                    binding.btnOpenFile.setOnClickListener { onOpenClicked(outputUri) }
                    binding.btnShareFile.setOnClickListener { onShareClicked(outputUri) }
                }

                is QueueItemStatus.Cancelled -> {
                    binding.tvSizeInfo.text = context.getString(
                        R.string.size_queued_format,
                        originalSizeFormatted
                    )
                    binding.tvStatusDetail.text = context.getString(R.string.status_cancelled)
                    binding.progressBar.visibility = View.GONE
                    binding.layoutCompletedActions.visibility = View.GONE
                    binding.btnCancelItem.visibility = View.VISIBLE
                    binding.btnCancelItem.text = context.getString(R.string.action_remove)
                }

                is QueueItemStatus.Failed -> {
                    binding.tvSizeInfo.text = context.getString(
                        R.string.size_queued_format,
                        originalSizeFormatted
                    )
                    binding.tvStatusDetail.text = context.getString(
                        R.string.status_failed_format,
                        status.reason
                    )
                    binding.progressBar.visibility = View.GONE
                    binding.layoutCompletedActions.visibility = View.GONE
                    binding.btnCancelItem.visibility = View.VISIBLE
                    binding.btnCancelItem.text = context.getString(R.string.action_remove)
                }
            }

            binding.btnCancelItem.setOnClickListener {
                onCancelOrRemoveClicked(item)
            }
        }

        private fun formatDuration(ms: Long): String {
            val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            return String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<QueueItem>() {
        override fun areItemsTheSame(oldItem: QueueItem, newItem: QueueItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: QueueItem, newItem: QueueItem): Boolean {
            return oldItem == newItem
        }
    }
}