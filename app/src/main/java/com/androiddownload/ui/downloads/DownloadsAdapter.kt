package com.androiddownload.ui.downloads

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import com.androiddownload.R
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import com.androiddownload.core.utils.DownloadSourceClassifier
import com.androiddownload.core.utils.FileSizeFormatter
import com.androiddownload.core.utils.YtDlpQualityOptions
import java.util.Locale

class DownloadsAdapter(
    context: Context,
    private val onItemClick: (DownloadEntity) -> Unit,
    private val onCancelClick: (DownloadEntity) -> Unit,
    private val onPauseClick: (DownloadEntity) -> Unit,
    private val onResumeClick: (DownloadEntity) -> Unit,
    private val onRetryClick: (DownloadEntity) -> Unit,
    private val onOpenClick: (DownloadEntity) -> Unit,
    private val onShareClick: (DownloadEntity) -> Unit
) : BaseAdapter() {
    private val inflater = LayoutInflater.from(context)
    private val items = mutableListOf<DownloadEntity>()

    fun submitList(downloads: List<DownloadEntity>) {
        items.clear()
        items.addAll(downloads)
        notifyDataSetChanged()
    }

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): DownloadEntity = items[position]

    override fun getItemId(position: Int): Long = items[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_download, parent, false)
        val holder = view.tag as? ViewHolder ?: ViewHolder(
            view,
            onItemClick,
            onCancelClick,
            onPauseClick,
            onResumeClick,
            onRetryClick,
            onOpenClick,
            onShareClick
        ).also { view.tag = it }
        holder.bind(getItem(position))
        return view
    }

    private class ViewHolder(
        view: View,
        private val onItemClick: (DownloadEntity) -> Unit,
        private val onCancelClick: (DownloadEntity) -> Unit,
        private val onPauseClick: (DownloadEntity) -> Unit,
        private val onResumeClick: (DownloadEntity) -> Unit,
        private val onRetryClick: (DownloadEntity) -> Unit,
        private val onOpenClick: (DownloadEntity) -> Unit,
        private val onShareClick: (DownloadEntity) -> Unit
    ) {
        private val root: View = view
        private val downloadTypeBadge: TextView = view.findViewById(R.id.downloadTypeBadge)
        private val fileNameText: TextView = view.findViewById(R.id.fileNameText)
        private val statusText: TextView = view.findViewById(R.id.statusText)
        private val formatText: TextView = view.findViewById(R.id.formatText)
        private val progressText: TextView = view.findViewById(R.id.progressText)
        private val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        private val sizeText: TextView = view.findViewById(R.id.sizeText)
        private val speedText: TextView = view.findViewById(R.id.speedText)
        private val actionButton: Button = view.findViewById(R.id.actionButton)
        private val openButton: Button = view.findViewById(R.id.openButton)
        private val shareButton: Button = view.findViewById(R.id.shareButton)
        private val cancelButton: Button = view.findViewById(R.id.cancelButton)
        private val errorText: TextView = view.findViewById(R.id.errorText)

        fun bind(download: DownloadEntity) {
            fileNameText.text = download.fileName
            statusText.text = statusLabel(download.status)
            statusText.setTextColor(statusColor(download.status))
            val formatLabel = YtDlpQualityOptions.labelForDownload(root.context, download)
            downloadTypeBadge.text = typeBadgeLabel(download, formatLabel)
            formatText.text = formatLabel
            formatText.visibility = if (formatLabel.isBlank()) View.GONE else View.VISIBLE
            val indeterminate = isIndeterminate(download)
            val progress = normalizedProgress(download)
            progressBar.isIndeterminate = indeterminate
            progressBar.progress = progress
            progressText.text = progressLabel(indeterminate, progress)
            sizeText.text = buildSizeText(download)
            speedText.text = FileSizeFormatter.formatSpeed(download.speed).ifBlank {
                root.context.getString(R.string.not_available)
            }
            val actionConfig = actionConfig(download)
            actionButton.visibility = if (actionConfig != null) View.VISIBLE else View.GONE
            actionButton.isEnabled = actionConfig != null
            actionButton.text = actionConfig?.first ?: ""
            actionButton.setOnClickListener {
                actionConfig?.second?.invoke(download)
            }
            val canCancel = canCancel(download)
            cancelButton.visibility = if (canCancel) View.VISIBLE else View.GONE
            cancelButton.isEnabled = canCancel
            cancelButton.setOnClickListener {
                onCancelClick(download)
            }
            val canOpen = download.status == DownloadStatus.COMPLETED
            openButton.visibility = if (canOpen) View.VISIBLE else View.GONE
            openButton.isEnabled = canOpen
            openButton.setOnClickListener {
                onOpenClick(download)
            }
            val canShare = download.status == DownloadStatus.COMPLETED
            shareButton.visibility = if (canShare) View.VISIBLE else View.GONE
            shareButton.isEnabled = canShare
            shareButton.setOnClickListener {
                onShareClick(download)
            }
            root.isClickable = true
            root.setOnClickListener { onItemClick(download) }

            if (download.status == DownloadStatus.FAILED && !download.errorMessage.isNullOrBlank()) {
                errorText.text = download.errorMessage
                errorText.visibility = View.VISIBLE
            } else {
                errorText.visibility = View.GONE
            }
        }

        private fun isIndeterminate(download: DownloadEntity): Boolean {
            return DownloadSourceClassifier.shouldUseHttpDownloader(download.sourceUrl) &&
                download.totalBytes <= 0 &&
                download.progress <= 0 &&
                (download.status == DownloadStatus.RUNNING || download.status == DownloadStatus.PREPARING)
        }

        private fun normalizedProgress(download: DownloadEntity): Int {
            return when (download.status) {
                DownloadStatus.COMPLETED -> 100
                else -> download.progress.coerceIn(0, 100)
            }
        }

        private fun progressLabel(indeterminate: Boolean, progress: Int): String {
            return if (indeterminate) {
                root.context.getString(R.string.download_progress_unknown)
            } else {
                "$progress%"
            }
        }

        private fun statusLabel(status: DownloadStatus): String {
            return when (status) {
                DownloadStatus.QUEUED -> root.context.getString(R.string.status_queued)
                DownloadStatus.PREPARING -> root.context.getString(R.string.status_preparing)
                DownloadStatus.RUNNING -> root.context.getString(R.string.status_running)
                DownloadStatus.PAUSED -> root.context.getString(R.string.status_paused)
                DownloadStatus.FAILED -> root.context.getString(R.string.status_failed)
                DownloadStatus.COMPLETED -> root.context.getString(R.string.status_completed)
                DownloadStatus.CANCELED -> root.context.getString(R.string.status_canceled)
            }
        }

        private fun statusColor(status: DownloadStatus): Int {
            val colorRes = when (status) {
                DownloadStatus.COMPLETED -> R.color.success
                DownloadStatus.FAILED,
                DownloadStatus.CANCELED -> R.color.danger
                DownloadStatus.PAUSED -> R.color.warning
                DownloadStatus.QUEUED,
                DownloadStatus.PREPARING,
                DownloadStatus.RUNNING -> R.color.brand
            }
            return root.context.getColor(colorRes)
        }

        private fun canCancel(download: DownloadEntity): Boolean {
            return download.status == DownloadStatus.QUEUED ||
                download.status == DownloadStatus.PREPARING ||
                download.status == DownloadStatus.RUNNING ||
                download.status == DownloadStatus.PAUSED
        }

        private fun actionConfig(download: DownloadEntity): Pair<String, ((DownloadEntity) -> Unit)>? {
            return when (download.status) {
                DownloadStatus.RUNNING ->
                    if (DownloadSourceClassifier.shouldUseHttpDownloader(download.sourceUrl)) {
                        Pair(
                            actionButton.context.getString(R.string.pause),
                            onPauseClick
                        )
                    } else {
                        null
                    }
                DownloadStatus.PAUSED ->
                    if (DownloadSourceClassifier.shouldUseHttpDownloader(download.sourceUrl)) {
                        Pair(
                            actionButton.context.getString(R.string.resume),
                            onResumeClick
                        )
                    } else {
                        null
                    }
                DownloadStatus.FAILED -> Pair(
                    actionButton.context.getString(R.string.retry),
                    onRetryClick
                )
                else -> null
            }
        }

        private fun buildSizeText(download: DownloadEntity): String {
            return when {
                download.totalBytes > 0 -> {
                    val downloaded = FileSizeFormatter.formatBytes(download.downloadedBytes)
                    val total = FileSizeFormatter.formatBytes(download.totalBytes)
                    "$downloaded / $total"
                }
                download.status == DownloadStatus.RUNNING ||
                    download.status == DownloadStatus.PREPARING ||
                    download.status == DownloadStatus.QUEUED -> {
                    root.context.getString(R.string.download_progress_unknown)
                }
                download.progress > 0 -> "${download.progress.coerceIn(0, 100)}%"
                download.downloadedBytes > 0 -> FileSizeFormatter.formatBytes(download.downloadedBytes)
                else -> root.context.getString(R.string.download_progress_unknown)
            }
        }

        private fun typeBadgeLabel(download: DownloadEntity, formatLabel: String): String {
            val label = formatLabel.uppercase(Locale.US)
            return when {
                "MP3" in label -> "MP3"
                "MP4" in label -> "MP4"
                DownloadSourceClassifier.shouldUseHttpDownloader(download.sourceUrl) -> "HTTP"
                else -> "AIO"
            }
        }
    }
}
