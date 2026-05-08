package com.androiddownload.ui.downloads

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import com.androiddownload.R
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import com.androiddownload.core.utils.DownloadSourceClassifier
import com.androiddownload.core.utils.FileSizeFormatter
import com.androiddownload.core.utils.YtDlpQualityOptions
import java.util.Locale

class DownloadsController(
    private val context: Context,
    downloadsList: ListView,
    private val emptyDownloadsText: TextView,
    private val searchInput: EditText,
    private val filterAllButton: Button,
    private val filterActiveButton: Button,
    private val filterPausedButton: Button,
    private val filterCompletedButton: Button,
    private val filterFailedButton: Button,
    private val activeDownloadCard: View,
    private val activeDownloadTitleText: TextView,
    private val activeDownloadNameText: TextView,
    private val activeDownloadFormatText: TextView,
    private val activeDownloadProgressText: TextView,
    private val activeDownloadProgressBar: ProgressBar,
    private val activeDownloadSpeedText: TextView,
    private val activeDownloadSizeText: TextView,
    private val activeDownloadActionsRow: View,
    private val activeDownloadPrimaryActionButton: Button,
    private val activeDownloadSecondaryActionButton: Button,
    private val callbacks: Callbacks
) {
    data class Callbacks(
        val onItemClick: (DownloadEntity) -> Unit,
        val onCancelClick: (DownloadEntity) -> Unit,
        val onPauseClick: (DownloadEntity) -> Unit,
        val onResumeClick: (DownloadEntity) -> Unit,
        val onRetryClick: (DownloadEntity) -> Unit,
        val onOpenClick: (DownloadEntity) -> Unit,
        val onShareClick: (DownloadEntity) -> Unit,
        val onRequestShowKeyboard: () -> Unit,
        val onRequestHideKeyboard: (View) -> Unit
    )

    private val adapter = DownloadsAdapter(
        context = context,
        onItemClick = callbacks.onItemClick,
        onCancelClick = callbacks.onCancelClick,
        onPauseClick = callbacks.onPauseClick,
        onResumeClick = callbacks.onResumeClick,
        onRetryClick = callbacks.onRetryClick,
        onOpenClick = callbacks.onOpenClick,
        onShareClick = callbacks.onShareClick
    )
    private var downloads: List<DownloadEntity> = emptyList()
    private var filter = DownloadsFilter.ALL
    private var searchQuery: String = ""

    init {
        downloadsList.adapter = adapter
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString().orEmpty()
                if (query == searchQuery) return
                searchQuery = query
                render()
            }
        })
        filterAllButton.setOnClickListener { setFilter(DownloadsFilter.ALL) }
        filterActiveButton.setOnClickListener { setFilter(DownloadsFilter.ACTIVE) }
        filterPausedButton.setOnClickListener { setFilter(DownloadsFilter.PAUSED) }
        filterCompletedButton.setOnClickListener { setFilter(DownloadsFilter.COMPLETED) }
        filterFailedButton.setOnClickListener { setFilter(DownloadsFilter.FAILED) }
        updateFilterUi()
    }

    fun submitDownloads(downloads: List<DownloadEntity>) {
        this.downloads = downloads
        render()
    }

    fun setFilter(filter: DownloadsFilter, refreshOnly: Boolean = false) {
        this.filter = filter
        updateFilterUi()
        if (!refreshOnly) {
            render()
        }
    }

    fun setSearchQuery(query: String, refreshOnly: Boolean = false) {
        val normalizedQuery = query.trim()
        searchQuery = normalizedQuery
        if (searchInput.text?.toString().orEmpty() != normalizedQuery) {
            searchInput.setText(normalizedQuery)
            searchInput.setSelection(normalizedQuery.length)
        }
        if (!refreshOnly) {
            render()
        }
    }

    fun clearSearch() {
        setSearchQuery("")
    }

    fun showSearch() {
        searchInput.visibility = View.VISIBLE
        searchInput.requestFocus()
        searchInput.post { callbacks.onRequestShowKeyboard() }
    }

    fun hideSearch(clearQuery: Boolean) {
        if (clearQuery && searchQuery.isNotBlank()) {
            setSearchQuery("")
        }
        searchInput.visibility = View.GONE
        searchInput.clearFocus()
        callbacks.onRequestHideKeyboard(searchInput)
    }

    fun toggleSearch() {
        if (searchInput.visibility == View.VISIBLE) {
            hideSearch(clearQuery = true)
        } else {
            showSearch()
        }
    }

    fun render() {
        val activeDownload = selectActiveDownload()
        updateActiveDownloadCard(activeDownload)
        val filteredDownloads = downloads
            .filter { activeDownload == null || it.id != activeDownload.id }
            .filter { it.matchesFilter(filter) }
            .filter { it.matchesSearch(searchQuery) }
        adapter.submitList(filteredDownloads)
        emptyDownloadsText.text = if (downloads.isEmpty()) {
            context.getString(R.string.empty_downloads)
        } else if (filteredDownloads.isEmpty()) {
            context.getString(R.string.downloads_empty_search)
        } else {
            context.getString(R.string.downloads_empty_filtered)
        }
        emptyDownloadsText.visibility = if (filteredDownloads.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateFilterUi() {
        val buttons = listOf(
            filterAllButton,
            filterActiveButton,
            filterPausedButton,
            filterCompletedButton,
            filterFailedButton
        )
        buttons.forEach { button ->
            val isSelected = when (button.id) {
                R.id.downloadsFilterAllButton -> filter == DownloadsFilter.ALL
                R.id.downloadsFilterActiveButton -> filter == DownloadsFilter.ACTIVE
                R.id.downloadsFilterPausedButton -> filter == DownloadsFilter.PAUSED
                R.id.downloadsFilterCompletedButton -> filter == DownloadsFilter.COMPLETED
                R.id.downloadsFilterFailedButton -> filter == DownloadsFilter.FAILED
                else -> false
            }
            button.isSelected = isSelected
            button.setBackgroundResource(
                if (isSelected) R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected
            )
            button.setTextColor(
                context.getColor(if (isSelected) R.color.brand else R.color.text_muted)
            )
        }
    }

    private fun selectActiveDownload(): DownloadEntity? {
        return downloads.firstDownloadByStatus(DownloadStatus.RUNNING)
            ?: downloads.firstDownloadByStatus(DownloadStatus.PREPARING)
            ?: downloads.firstDownloadByStatus(DownloadStatus.QUEUED)
            ?: downloads.firstDownloadByStatus(DownloadStatus.PAUSED)
    }

    private fun updateActiveDownloadCard(activeDownload: DownloadEntity?) {
        if (activeDownload == null) {
            activeDownloadCard.visibility = View.GONE
            activeDownloadCard.setOnClickListener(null)
            activeDownloadActionsRow.visibility = View.GONE
            return
        }

        val progress = normalizedProgress(activeDownload)
        val indeterminate = isIndeterminateDownload(activeDownload)
        activeDownloadCard.visibility = View.VISIBLE
        activeDownloadCard.setOnClickListener { callbacks.onItemClick(activeDownload) }
        activeDownloadTitleText.text = activeDownloadCardTitle(activeDownload.status)
        activeDownloadNameText.text = activeDownload.fileName
        activeDownloadFormatText.text =
            "${formatLabel(activeDownload)} - ${downloadStatusLabel(activeDownload.status)}"
        activeDownloadProgressText.text = progressLabel(activeDownload, indeterminate, progress)
        activeDownloadProgressBar.isIndeterminate = indeterminate
        activeDownloadProgressBar.progress = progress
        activeDownloadSpeedText.text = formatSpeed(activeDownload.speed)
        activeDownloadSizeText.text = summaryDownloadSizeText(activeDownload)
        bindActiveDownloadActions(activeDownload)
    }

    private fun bindActiveDownloadActions(download: DownloadEntity) {
        val actions = activeDownloadActions(download)
        activeDownloadActionsRow.visibility = if (actions.isEmpty()) View.GONE else View.VISIBLE
        bindActiveActionButton(activeDownloadPrimaryActionButton, actions.getOrNull(0), primary = true)
        bindActiveActionButton(activeDownloadSecondaryActionButton, actions.getOrNull(1), primary = false)
    }

    private fun bindActiveActionButton(
        button: Button,
        action: Pair<String, () -> Unit>?,
        primary: Boolean
    ) {
        button.visibility = if (action == null) View.GONE else View.VISIBLE
        button.isEnabled = action != null
        button.text = action?.first.orEmpty()
        button.setBackgroundResource(if (primary) R.drawable.bg_button_primary else R.drawable.bg_button_secondary)
        button.setTextColor(
            context.getColor(if (primary) R.color.button_primary_text else R.color.button_secondary_text)
        )
        button.setOnClickListener { action?.second?.invoke() }
    }

    private fun activeDownloadActions(download: DownloadEntity): List<Pair<String, () -> Unit>> {
        val isHttp = DownloadSourceClassifier.shouldUseHttpDownloader(download.sourceUrl)
        return when (download.status) {
            DownloadStatus.RUNNING -> {
                if (isHttp) {
                    listOf(
                        context.getString(R.string.pause) to { callbacks.onPauseClick(download) },
                        context.getString(R.string.cancel) to { callbacks.onCancelClick(download) }
                    )
                } else {
                    listOf(context.getString(R.string.cancel) to { callbacks.onCancelClick(download) })
                }
            }
            DownloadStatus.PREPARING,
            DownloadStatus.QUEUED -> listOf(
                context.getString(R.string.cancel) to { callbacks.onCancelClick(download) }
            )
            DownloadStatus.PAUSED -> {
                if (isHttp) {
                    listOf(
                        context.getString(R.string.resume) to { callbacks.onResumeClick(download) },
                        context.getString(R.string.cancel) to { callbacks.onCancelClick(download) }
                    )
                } else {
                    listOf(context.getString(R.string.cancel) to { callbacks.onCancelClick(download) })
                }
            }
            DownloadStatus.COMPLETED -> listOf(
                context.getString(R.string.open) to { callbacks.onOpenClick(download) },
                context.getString(R.string.share) to { callbacks.onShareClick(download) }
            )
            DownloadStatus.FAILED -> listOf(
                context.getString(R.string.retry) to { callbacks.onRetryClick(download) }
            )
            DownloadStatus.CANCELED -> emptyList()
        }
    }

    private fun DownloadEntity.matchesFilter(filter: DownloadsFilter): Boolean {
        return when (filter) {
            DownloadsFilter.ALL -> true
            DownloadsFilter.ACTIVE ->
                status == DownloadStatus.QUEUED ||
                    status == DownloadStatus.PREPARING ||
                    status == DownloadStatus.RUNNING
            DownloadsFilter.PAUSED -> status == DownloadStatus.PAUSED
            DownloadsFilter.COMPLETED -> status == DownloadStatus.COMPLETED
            DownloadsFilter.FAILED ->
                status == DownloadStatus.FAILED ||
                    status == DownloadStatus.CANCELED
        }
    }

    private fun DownloadEntity.matchesSearch(query: String): Boolean {
        if (query.isBlank()) return true
        val normalizedQuery = query.lowercase(Locale.getDefault())
        val searchBlob = buildString {
            append(fileName)
            append(' ')
            append(sourceUrl)
            append(' ')
            append(downloadStatusLabel(status))
            append(' ')
            append(formatLabel(this@matchesSearch))
        }.lowercase(Locale.getDefault())
        return searchBlob.contains(normalizedQuery)
    }

    private fun List<DownloadEntity>.firstDownloadByStatus(status: DownloadStatus): DownloadEntity? {
        return firstOrNull { it.status == status }
    }

    private fun activeDownloadCardTitle(status: DownloadStatus): String {
        return when (status) {
            DownloadStatus.QUEUED -> context.getString(R.string.status_queued)
            DownloadStatus.PREPARING -> context.getString(R.string.status_preparing)
            DownloadStatus.PAUSED -> context.getString(R.string.status_paused)
            else -> context.getString(R.string.downloads_active_title)
        }
    }

    private fun isIndeterminateDownload(download: DownloadEntity): Boolean {
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
        return when (download.status) {
            DownloadStatus.QUEUED -> context.getString(R.string.status_queued)
            DownloadStatus.PREPARING -> context.getString(R.string.status_preparing_progress)
            DownloadStatus.RUNNING -> {
                val prefix = if (indeterminate) "" else "$progress% "
                prefix + context.getString(R.string.download_progress_unknown)
            }
            DownloadStatus.PAUSED -> if (progress > 0) {
                "$progress% ${context.getString(R.string.status_paused)}"
            } else {
                context.getString(R.string.status_paused)
            }
            DownloadStatus.COMPLETED -> "100% ${context.getString(R.string.status_completed)}"
            DownloadStatus.FAILED -> context.getString(R.string.status_failed)
            DownloadStatus.CANCELED -> context.getString(R.string.status_canceled)
        }
    }

    private fun summaryDownloadSizeText(download: DownloadEntity): String {
        return when {
            download.totalBytes > 0 -> {
                val downloaded = FileSizeFormatter.formatBytes(download.downloadedBytes)
                val total = FileSizeFormatter.formatBytes(download.totalBytes)
                "$downloaded / $total"
            }
            download.downloadedBytes > 0 -> FileSizeFormatter.formatBytes(download.downloadedBytes)
            download.progress > 0 -> "${download.progress.coerceIn(0, 100)}%"
            else -> ""
        }
    }

    private fun formatLabel(download: DownloadEntity): String {
        val label = YtDlpQualityOptions.labelForDownload(context, download)
        return if (label.isBlank()) context.getString(R.string.download_direct) else label
    }

    private fun downloadStatusLabel(status: DownloadStatus): String {
        return when (status) {
            DownloadStatus.QUEUED -> context.getString(R.string.status_queued)
            DownloadStatus.PREPARING -> context.getString(R.string.status_preparing)
            DownloadStatus.RUNNING -> context.getString(R.string.status_running)
            DownloadStatus.PAUSED -> context.getString(R.string.status_paused)
            DownloadStatus.FAILED -> context.getString(R.string.status_failed)
            DownloadStatus.COMPLETED -> context.getString(R.string.status_completed)
            DownloadStatus.CANCELED -> context.getString(R.string.status_canceled)
        }
    }

    private fun formatSpeed(speed: Long): String {
        return if (speed > 0) {
            FileSizeFormatter.formatSpeed(speed)
        } else {
            context.getString(R.string.not_available)
        }
    }
}
