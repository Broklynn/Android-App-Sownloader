package com.androiddownload.ui.downloads

import android.content.Context
import android.net.Uri
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
import com.androiddownload.core.utils.DownloadErrorFormatter
import com.androiddownload.core.utils.DownloadSourceClassifier
import com.androiddownload.core.utils.FileSizeFormatter
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
            fileNameText.text = displayTitle(download)
            statusText.text = statusLabel(download.status)
            statusText.setTextColor(statusColor(download.status))
            val formatLabel = DownloadFormatLabelFormatter.labelForDownload(root.context, download)
            downloadTypeBadge.text = typeBadgeLabel(download, formatLabel)
            formatText.text = formatLabel
            formatText.visibility = if (formatLabel.isBlank()) View.GONE else View.VISIBLE
            val indeterminate = isIndeterminate(download)
            val progress = normalizedProgress(download)
            progressBar.isIndeterminate = indeterminate
            progressBar.progress = progress
            progressText.text = progressLabel(download, indeterminate, progress)
            val sizeLabel = buildSizeText(download)
            sizeText.text = sizeLabel
            sizeText.visibility = if (sizeLabel.isBlank()) View.GONE else View.VISIBLE
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
                errorText.text = DownloadErrorFormatter.friendlyMessage(root.context, download.errorMessage)
                errorText.visibility = View.VISIBLE
            } else {
                errorText.visibility = View.GONE
            }
        }

        private fun displayTitle(download: DownloadEntity): String {
            val unavailable = root.context.getString(R.string.not_available)
            val rawFileName = download.fileName.trim()
            if (rawFileName.isNotBlank() && !rawFileName.equals(unavailable, ignoreCase = true)) {
                return rawFileName
            }
            val urlName = runCatching {
                Uri.decode(Uri.parse(download.sourceUrl).lastPathSegment.orEmpty())
            }.getOrDefault("").substringBefore('?').substringBefore('#').trim()
            if (urlName.isNotBlank()) return urlName
            return if (download.status == DownloadStatus.FAILED) {
                root.context.getString(R.string.download_title_failed)
            } else {
                root.context.getString(R.string.download_title_unknown)
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

        private fun progressLabel(download: DownloadEntity, indeterminate: Boolean, progress: Int): String {
            return DownloadStatusTextFormatter.progressLabel(
                status = download.status,
                progress = progress,
                indeterminate = indeterminate,
                labels = downloadStatusTextLabels()
            )
        }

        private fun statusLabel(status: DownloadStatus): String {
            return DownloadStatusTextFormatter.statusLabel(
                status = status,
                labels = downloadStatusTextLabels()
            )
        }

        private fun downloadStatusTextLabels(): DownloadStatusTextLabels {
            return DownloadStatusTextLabels(
                queued = root.context.getString(R.string.status_queued),
                preparing = root.context.getString(R.string.status_preparing),
                preparingProgress = root.context.getString(R.string.status_preparing_progress),
                running = root.context.getString(R.string.status_running),
                paused = root.context.getString(R.string.status_paused),
                completed = root.context.getString(R.string.status_completed),
                failed = root.context.getString(R.string.status_failed),
                canceled = root.context.getString(R.string.status_canceled),
                downloadingUnknown = root.context.getString(R.string.download_progress_unknown)
            )
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
                    ""
                }
                download.progress > 0 -> "${download.progress.coerceIn(0, 100)}%"
                download.downloadedBytes > 0 -> FileSizeFormatter.formatBytes(download.downloadedBytes)
                else -> ""
            }
        }

        private fun typeBadgeLabel(download: DownloadEntity, formatLabel: String): String {
            val label = formatLabel.uppercase(Locale.US)
            return when {
                "MP3" in label -> "MP3"
                "MP4" in label -> "MP4"
                DownloadSourceClassifier.shouldUseHttpDownloader(download.sourceUrl) -> "HTTP"
                else -> "MIDIA"
            }
        }
    }
}
