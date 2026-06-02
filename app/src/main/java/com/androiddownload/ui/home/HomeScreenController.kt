package com.androiddownload.ui.home

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.preferences.RecentDownloadsStore

class HomeScreenController(
    activity: Activity,
    urlInput: EditText,
    downloadButton: Button,
    errorText: TextView,
    recentUrlsSection: View,
    recentUrlsList: LinearLayout,
    clearRecentUrlsButton: View,
    recentDownloadsSection: View,
    recentDownloadsList: LinearLayout,
    recentDownloadsStore: RecentDownloadsStore,
    formatLabelProvider: (DownloadEntity) -> String,
    statusLabelProvider: (DownloadEntity) -> String,
    sizeTextProvider: (DownloadEntity) -> String,
    badgeLabelProvider: (DownloadEntity, String) -> String,
    onRecentDownloadSelected: (DownloadEntity) -> Unit,
    onClipboardAccepted: (String) -> Unit
) {
    val homeController = HomeController(
        urlInput = urlInput,
        downloadButton = downloadButton,
        errorText = errorText
    )

    var onDownloadRequested: (String) -> Unit = {}

    private val clipboardLinkPromptController = ClipboardLinkPromptController(
        activity = activity,
        onUseUrl = onClipboardAccepted
    )

    private val recentUrlController = HomeRecentUrlController(
        store = recentDownloadsStore,
        section = recentUrlsSection,
        list = recentUrlsList,
        clearButton = clearRecentUrlsButton,
        homeController = homeController
    )

    private val recentDownloadsRenderer = HomeRecentDownloadsRenderer(
        context = activity,
        section = recentDownloadsSection,
        list = recentDownloadsList,
        formatLabelProvider = formatLabelProvider,
        statusLabelProvider = statusLabelProvider,
        sizeTextProvider = sizeTextProvider,
        badgeLabelProvider = badgeLabelProvider,
        onItemClick = onRecentDownloadSelected
    )

    init {
        homeController.onDownloadClick = { rawUrl -> onDownloadRequested(rawUrl) }
    }

    fun setUrl(url: String) {
        homeController.setUrl(url)
    }

    fun showError(message: String) {
        homeController.showError(message)
    }

    fun focusUrlInput() {
        homeController.focusUrlInput()
    }

    fun addRecentDownloadUrl(url: String) {
        recentUrlController.addUrl(url)
    }

    fun renderRecentUrls() {
        recentUrlController.render()
    }

    fun renderRecentDownloads(downloads: List<DownloadEntity>) {
        recentDownloadsRenderer.render(downloads.take(MAX_RECENT_DOWNLOADS_DISPLAYED))
    }

    fun maybeShowClipboardPrompt(intent: Intent?) {
        clipboardLinkPromptController.maybePrompt(intent)
    }

    private companion object {
        private const val MAX_RECENT_DOWNLOADS_DISPLAYED = 4
    }
}
