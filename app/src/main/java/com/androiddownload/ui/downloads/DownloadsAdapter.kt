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

class DownloadsAdapter(
    context: Context,
    private val onCancelClick: (DownloadEntity) -> Unit,
    private val onPauseClick: (DownloadEntity) -> Unit,
    private val onResumeClick: (DownloadEntity) -> Unit,
    private val onRetryClick: (DownloadEntity) -> Unit,
    private val onOpenClick: (DownloadEntity) -> Unit
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
            onCancelClick,
            onPauseClick,
            onResumeClick,
            onRetryClick,
            onOpenClick
        ).also { view.tag = it }
        holder.bind(getItem(position))
        return view
    }

    private class ViewHolder(
        view: View,
        private val onCancelClick: (DownloadEntity) -> Unit,
        private val onPauseClick: (DownloadEntity) -> Unit,
        private val onResumeClick: (DownloadEntity) -> Unit,
        private val onRetryClick: (DownloadEntity) -> Unit,
        private val onOpenClick: (DownloadEntity) -> Unit
    ) {
        private val root: View = view
        private val fileNameText: TextView = view.findViewById(R.id.fileNameText)
        private val statusText: TextView = view.findViewById(R.id.statusText)
        private val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        private val sizeText: TextView = view.findViewById(R.id.sizeText)
        private val speedText: TextView = view.findViewById(R.id.speedText)
        private val actionButton: Button = view.findViewById(R.id.actionButton)
        private val openButton: Button = view.findViewById(R.id.openButton)
        private val cancelButton: Button = view.findViewById(R.id.cancelButton)
        private val errorText: TextView = view.findViewById(R.id.errorText)

        fun bind(download: DownloadEntity) {
            fileNameText.text = download.fileName
            statusText.text = download.status.name
            progressBar.isIndeterminate = isIndeterminate(download)
            progressBar.progress = download.progress
            sizeText.text = buildSizeText(download)
            speedText.text = FileSizeFormatter.formatSpeed(download.speed)
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
            root.isClickable = canOpen
            root.setOnClickListener(
                if (canOpen) {
                    View.OnClickListener { onOpenClick(download) }
                } else {
                    null
                }
            )

            if (download.status == DownloadStatus.FAILED && !download.errorMessage.isNullOrBlank()) {
                errorText.text = download.errorMessage
                errorText.visibility = View.VISIBLE
            } else {
                errorText.visibility = View.GONE
            }
        }

        private fun isIndeterminate(download: DownloadEntity): Boolean {
            return download.totalBytes <= 0 &&
                (download.status == DownloadStatus.RUNNING || download.status == DownloadStatus.PREPARING)
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
            val downloaded = FileSizeFormatter.formatBytes(download.downloadedBytes)
            val total = FileSizeFormatter.formatBytes(download.totalBytes)
            return "$downloaded / $total"
        }
    }
}
