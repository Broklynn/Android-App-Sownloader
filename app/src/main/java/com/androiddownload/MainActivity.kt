package com.androiddownload

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Environment
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.widget.ScrollView
import android.text.Editable
import android.text.TextWatcher
import androidx.core.content.FileProvider
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import com.androiddownload.core.utils.FileSizeFormatter
import com.androiddownload.core.utils.DownloadSourceClassifier
import com.androiddownload.core.utils.UrlValidator
import com.androiddownload.core.utils.YtDlpQualityOptions
import com.androiddownload.core.utils.YtDlpDiagnostics
import com.androiddownload.download.service.DownloadForegroundService
import com.androiddownload.ui.downloads.DownloadsAdapter
import com.androiddownload.ui.home.HomeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.io.File
import java.util.Locale
import org.json.JSONArray
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var appHeader: View
    private lateinit var mainTabBar: View
    private lateinit var homeContainer: View
    private lateinit var browserContainer: View
    private lateinit var downloadsContainer: View
    private lateinit var settingsContainer: View
    private lateinit var settingsMenuButton: ImageButton
    private lateinit var emptyDownloadsText: TextView
    private lateinit var clearFinishedButton: Button
    private lateinit var downloadsSearchInput: EditText
    private lateinit var recentDownloadsSection: LinearLayout
    private lateinit var clearRecentButton: Button
    private lateinit var recentDownloadsList: LinearLayout
    private lateinit var homeRecentDownloadsSection: LinearLayout
    private lateinit var homeRecentDownloadsList: LinearLayout
    private lateinit var downloadsFilterAllButton: Button
    private lateinit var downloadsFilterActiveButton: Button
    private lateinit var downloadsFilterPausedButton: Button
    private lateinit var downloadsFilterCompletedButton: Button
    private lateinit var downloadsFilterFailedButton: Button
    private lateinit var activeDownloadCard: View
    private lateinit var activeDownloadTitleText: TextView
    private lateinit var activeDownloadNameText: TextView
    private lateinit var activeDownloadFormatText: TextView
    private lateinit var activeDownloadProgressText: TextView
    private lateinit var activeDownloadProgressBar: ProgressBar
    private lateinit var activeDownloadSpeedText: TextView
    private lateinit var activeDownloadSizeText: TextView
    private lateinit var downloadLocationCard: View
    private lateinit var downloadLocationText: TextView
    private lateinit var ytdlpUpdateStatusText: TextView
    private lateinit var updateYtDlpButton: Button
    private lateinit var autoUpdateYtDlpButton: Button
    private lateinit var diagnosticsButton: Button
    private lateinit var aboutAppButton: Button
    private lateinit var settingsCloseButton: Button
    private lateinit var homeController: HomeController
    private lateinit var adapter: DownloadsAdapter
    private lateinit var homeTabButton: Button
    private lateinit var downloadsTabButton: Button
    private lateinit var browserTabButton: Button
    private lateinit var defaultQualityValueText: TextView
    private lateinit var defaultQualityButton: Button
    private lateinit var browserUrlInput: EditText
    private lateinit var browserGoButton: Button
    private lateinit var browserBackButton: Button
    private lateinit var browserForwardButton: Button
    private lateinit var browserReloadButton: Button
    private lateinit var browserDownloadButton: Button
    private lateinit var browserDetectedMediaButton: Button
    private lateinit var browserProgressBar: ProgressBar
    private lateinit var browserErrorText: TextView
    private lateinit var browserWebView: WebView
    private var browserPageLoading = false
    private var hasActiveDownloads = false
    private var ytDlpUpdateInProgress = false
    private var ytDlpUpdateMessage: String? = null
    private var currentDownloads: List<DownloadEntity> = emptyList()
    private var downloadsFilter = DownloadsFilter.ALL
    private var downloadsSearchQuery: String = ""
    private var currentScreen = PrimaryScreen.HOME
    private val detectedMediaLock = Any()
    private val detectedMediaCandidates = LinkedHashMap<String, MediaCandidate>()
    private val settingsPreferences: SharedPreferences
        get() = getSharedPreferences(SETTINGS_PREFS_NAME, MODE_PRIVATE)
    private val app: AndroidDownloadApp
        get() = application as AndroidDownloadApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestNotificationPermission()

        val app = application as AndroidDownloadApp
        homeController = HomeController(
            urlInput = findViewById(R.id.urlInput),
            downloadButton = findViewById(R.id.downloadButton),
            errorText = findViewById(R.id.urlErrorText)
        )

        appHeader = findViewById(R.id.appHeader)
        mainTabBar = findViewById(R.id.mainTabBar)
        homeContainer = findViewById(R.id.homeContainer)
        browserContainer = findViewById(R.id.browserContainer)
        downloadsContainer = findViewById(R.id.downloadsContainer)
        settingsContainer = findViewById(R.id.settingsContainer)
        settingsMenuButton = findViewById(R.id.settingsMenuButton)
        emptyDownloadsText = findViewById(R.id.emptyDownloadsText)
        clearFinishedButton = findViewById(R.id.clearFinishedButton)
        downloadsSearchInput = findViewById(R.id.downloadsSearchInput)
        recentDownloadsSection = findViewById(R.id.recentDownloadsSection)
        clearRecentButton = findViewById(R.id.clearRecentButton)
        recentDownloadsList = findViewById(R.id.recentDownloadsList)
        homeRecentDownloadsSection = findViewById(R.id.homeRecentDownloadsSection)
        homeRecentDownloadsList = findViewById(R.id.homeRecentDownloadsList)
        downloadsFilterAllButton = findViewById(R.id.downloadsFilterAllButton)
        downloadsFilterActiveButton = findViewById(R.id.downloadsFilterActiveButton)
        downloadsFilterPausedButton = findViewById(R.id.downloadsFilterPausedButton)
        downloadsFilterCompletedButton = findViewById(R.id.downloadsFilterCompletedButton)
        downloadsFilterFailedButton = findViewById(R.id.downloadsFilterFailedButton)
        activeDownloadCard = findViewById(R.id.activeDownloadCard)
        activeDownloadTitleText = findViewById(R.id.activeDownloadTitleText)
        activeDownloadNameText = findViewById(R.id.activeDownloadNameText)
        activeDownloadFormatText = findViewById(R.id.activeDownloadFormatText)
        activeDownloadProgressText = findViewById(R.id.activeDownloadProgressText)
        activeDownloadProgressBar = findViewById(R.id.activeDownloadProgressBar)
        activeDownloadSpeedText = findViewById(R.id.activeDownloadSpeedText)
        activeDownloadSizeText = findViewById(R.id.activeDownloadSizeText)
        downloadLocationCard = findViewById(R.id.downloadLocationCard)
        downloadLocationText = findViewById(R.id.downloadLocationText)
        ytdlpUpdateStatusText = findViewById(R.id.ytdlpUpdateStatusText)
        updateYtDlpButton = findViewById(R.id.updateYtDlpButton)
        autoUpdateYtDlpButton = findViewById(R.id.autoUpdateYtDlpButton)
        diagnosticsButton = findViewById(R.id.diagnosticsButton)
        aboutAppButton = findViewById(R.id.aboutAppButton)
        settingsCloseButton = findViewById(R.id.settingsCloseButton)
        homeTabButton = findViewById(R.id.homeTabButton)
        downloadsTabButton = findViewById(R.id.downloadsTabButton)
        browserTabButton = findViewById(R.id.browserTabButton)
        defaultQualityValueText = findViewById(R.id.defaultQualityValueText)
        defaultQualityButton = findViewById(R.id.defaultQualityButton)
        browserUrlInput = findViewById(R.id.browserUrlInput)
        browserGoButton = findViewById(R.id.browserGoButton)
        browserBackButton = findViewById(R.id.browserBackButton)
        browserForwardButton = findViewById(R.id.browserForwardButton)
        browserReloadButton = findViewById(R.id.browserReloadButton)
        browserDownloadButton = findViewById(R.id.browserDownloadButton)
        browserDetectedMediaButton = findViewById(R.id.browserDetectedMediaButton)
        browserProgressBar = findViewById(R.id.browserProgressBar)
        browserErrorText = findViewById(R.id.browserErrorText)
        browserWebView = findViewById(R.id.browserWebView)
        setupBrowserWebView()

        adapter = DownloadsAdapter(
            context = this,
            onItemClick = { download ->
                showDownloadDetailsDialog(download)
            },
            onCancelClick = { download ->
                DownloadForegroundService.cancel(this, download.id)
            },
            onPauseClick = { download ->
                DownloadForegroundService.pause(this, download.id)
            },
            onResumeClick = { download ->
                DownloadForegroundService.resume(this, download.id)
            },
            onRetryClick = { download ->
                DownloadForegroundService.retry(this, download.id)
            },
            onOpenClick = { download ->
                openCompletedDownload(download)
            },
            onShareClick = { download ->
                shareCompletedDownload(download)
            }
        )
        findViewById<ListView>(R.id.downloadsList).adapter = adapter

        homeTabButton.setOnClickListener { showHome() }
        downloadsTabButton.setOnClickListener { showDownloads() }
        browserTabButton.setOnClickListener { showBrowser() }
        settingsMenuButton.setOnClickListener { showSettings() }
        settingsCloseButton.setOnClickListener { closeSettingsOverlay() }
        defaultQualityButton.setOnClickListener { showDefaultQualityDialog() }
        browserGoButton.setOnClickListener { loadBrowserPage() }
        browserBackButton.setOnClickListener {
            if (browserWebView.canGoBack()) {
                browserWebView.goBack()
            }
            updateBrowserNavigationButtons()
        }
        browserForwardButton.setOnClickListener {
            if (browserWebView.canGoForward()) {
                browserWebView.goForward()
            }
            updateBrowserNavigationButtons()
        }
        browserReloadButton.setOnClickListener {
            if (!browserWebView.url.isNullOrBlank() && browserWebView.url != "about:blank") {
                clearBrowserError()
                browserWebView.reload()
            }
        }
        browserDownloadButton.setOnClickListener { downloadCurrentBrowserPage() }
        browserDetectedMediaButton.setOnClickListener { showDetectedMediaDialog() }
        clearFinishedButton.setOnClickListener { showClearFinishedDownloadsDialog() }
        clearRecentButton.setOnClickListener {
            saveRecentDownloadUrls(emptyList())
            renderRecentDownloads()
        }
        updateYtDlpButton.setOnClickListener { updateYtDlpManually() }
        autoUpdateYtDlpButton.setOnClickListener { toggleAutoUpdateYtDlp() }
        diagnosticsButton.setOnClickListener { showDiagnosticsDialog() }
        aboutAppButton.setOnClickListener { showAboutDialog() }
        downloadsSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString().orEmpty()
                if (query == downloadsSearchQuery) return
                downloadsSearchQuery = query
                renderDownloadsList()
            }
        })
        downloadsFilterAllButton.setOnClickListener { setDownloadsFilter(DownloadsFilter.ALL) }
        downloadsFilterActiveButton.setOnClickListener { setDownloadsFilter(DownloadsFilter.ACTIVE) }
        downloadsFilterPausedButton.setOnClickListener { setDownloadsFilter(DownloadsFilter.PAUSED) }
        downloadsFilterCompletedButton.setOnClickListener { setDownloadsFilter(DownloadsFilter.COMPLETED) }
        downloadsFilterFailedButton.setOnClickListener { setDownloadsFilter(DownloadsFilter.FAILED) }

        homeController.onDownloadClick = onDownloadClick@{ rawUrl ->
            handleDownloadRequest(
                rawUrl = rawUrl,
                onError = homeController::showError,
                homeController = homeController
            )
        }

        scope.launch {
            app.container.repository.observeDownloads().collectLatest { downloads ->
                currentDownloads = downloads
                renderDownloadsList()
                renderHomeRecentDownloads()
                hasActiveDownloads = downloads.any {
                    it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.PREPARING
                }
                updateYtDlpUpdateUiState()
            }
        }

        updateDefaultQualityText()
        updateDownloadLocationText()
        renderHomeRecentDownloads()
        renderRecentDownloads()
        showHome()
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        browserWebView.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun showHome() {
        currentScreen = PrimaryScreen.HOME
        appHeader.visibility = View.VISIBLE
        mainTabBar.visibility = View.VISIBLE
        homeContainer.visibility = View.VISIBLE
        browserContainer.visibility = View.GONE
        downloadsContainer.visibility = View.GONE
        settingsContainer.visibility = View.GONE
        renderHomeRecentDownloads()
        renderRecentDownloads()
        updateSelectedTab(homeTabButton)
    }

    private fun showDownloads() {
        currentScreen = PrimaryScreen.DOWNLOADS
        appHeader.visibility = View.VISIBLE
        mainTabBar.visibility = View.VISIBLE
        homeContainer.visibility = View.GONE
        browserContainer.visibility = View.GONE
        downloadsContainer.visibility = View.VISIBLE
        settingsContainer.visibility = View.GONE
        setDownloadsFilter(DownloadsFilter.ALL, refreshOnly = true)
        setDownloadsSearchQuery("", refreshOnly = true)
        updateSelectedTab(downloadsTabButton)
    }

    private fun showClearFinishedDownloadsDialog() {
        showDarkMessageDialog(
            title = getString(R.string.clear_finished_downloads_title),
            message = getString(R.string.clear_finished_downloads_message),
            buttons = listOf(
                DarkDialogButton(getString(android.R.string.cancel)),
                DarkDialogButton(getString(android.R.string.ok), primary = true) {
                clearFinishedDownloads()
                }
            )
        )
    }

    private fun setDownloadsFilter(filter: DownloadsFilter, refreshOnly: Boolean = false) {
        downloadsFilter = filter
        updateDownloadsFilterUi()
        if (!refreshOnly) {
            renderDownloadsList()
        }
    }

    private fun setDownloadsSearchQuery(query: String, refreshOnly: Boolean = false) {
        val normalizedQuery = query.trim()
        downloadsSearchQuery = normalizedQuery
        if (downloadsSearchInput.text?.toString().orEmpty() != normalizedQuery) {
            downloadsSearchInput.setText(normalizedQuery)
            downloadsSearchInput.setSelection(normalizedQuery.length)
        }
        if (!refreshOnly) {
            renderDownloadsList()
        }
    }

    private fun renderDownloadsList() {
        updateActiveDownloadCard()
        val filteredDownloads = currentDownloads
            .filter { it.matchesDownloadsFilter(downloadsFilter) }
            .filter { it.matchesDownloadsSearch(downloadsSearchQuery) }
        adapter.submitList(filteredDownloads)
        emptyDownloadsText.text = if (currentDownloads.isEmpty()) {
            getString(R.string.empty_downloads)
        } else if (filteredDownloads.isEmpty()) {
            getString(R.string.downloads_empty_search)
        } else {
            getString(R.string.downloads_empty_filtered)
        }
        emptyDownloadsText.visibility = if (filteredDownloads.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateDownloadsFilterUi() {
        val selectedColor = R.color.brand
        val unselectedColor = R.color.text_muted
        val buttons = listOf(
            downloadsFilterAllButton,
            downloadsFilterActiveButton,
            downloadsFilterPausedButton,
            downloadsFilterCompletedButton,
            downloadsFilterFailedButton
        )
        buttons.forEach { button ->
            val isSelected = when (button.id) {
                R.id.downloadsFilterAllButton -> downloadsFilter == DownloadsFilter.ALL
                R.id.downloadsFilterActiveButton -> downloadsFilter == DownloadsFilter.ACTIVE
                R.id.downloadsFilterPausedButton -> downloadsFilter == DownloadsFilter.PAUSED
                R.id.downloadsFilterCompletedButton -> downloadsFilter == DownloadsFilter.COMPLETED
                R.id.downloadsFilterFailedButton -> downloadsFilter == DownloadsFilter.FAILED
                else -> false
            }
            button.isSelected = isSelected
            button.setBackgroundResource(
                if (isSelected) R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected
            )
            button.setTextColor(getColor(if (isSelected) selectedColor else unselectedColor))
        }
    }

    private fun renderHomeRecentDownloads() {
        homeRecentDownloadsSection.visibility = View.VISIBLE
        homeRecentDownloadsList.removeAllViews()

        val downloads = currentDownloads.take(MAX_HOME_RECENT_DOWNLOADS_DISPLAYED)
        if (downloads.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = getString(R.string.home_recent_downloads_empty)
                setTextColor(getColor(R.color.text_secondary))
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setBackgroundResource(R.drawable.bg_empty_state)
                setPadding(dp(16), dp(18), dp(16), dp(18))
            }
            homeRecentDownloadsList.addView(
                emptyText,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            return
        }

        downloads.forEachIndexed { index, download ->
            val card = buildHomeRecentDownloadCard(download)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (index > 0) {
                params.topMargin = dp(10)
            }
            homeRecentDownloadsList.addView(card, params)
        }
    }

    private fun buildHomeRecentDownloadCard(download: DownloadEntity): View {
        val formatLabel = formatLabelForDetails(download)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_download_item)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isClickable = true
            setOnClickListener { showDownloadDetailsDialog(download) }

            addView(
                TextView(context).apply {
                    text = downloadTypeBadgeLabel(download, formatLabel)
                    gravity = android.view.Gravity.CENTER
                    setBackgroundResource(R.drawable.bg_media_art_placeholder)
                    setTextColor(getColor(R.color.background_main))
                    textSize = 12f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                },
                LinearLayout.LayoutParams(dp(56), dp(56))
            )

            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), 0, 0, 0)

                    addView(
                        TextView(context).apply {
                            text = download.fileName
                            setTextColor(getColor(R.color.text_primary))
                            textSize = 15f
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                            maxLines = 1
                            ellipsize = android.text.TextUtils.TruncateAt.END
                        },
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    )

                    addView(
                        TextView(context).apply {
                            text = "$formatLabel - ${downloadStatusLabel(download.status)}"
                            setTextColor(getColor(R.color.text_secondary))
                            textSize = 12f
                            maxLines = 1
                            ellipsize = android.text.TextUtils.TruncateAt.END
                        },
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = dp(5)
                        }
                    )

                    addView(
                        TextView(context).apply {
                            text = summaryDownloadSizeText(download)
                            setTextColor(getColor(R.color.text_muted))
                            textSize = 12f
                            maxLines = 1
                            ellipsize = android.text.TextUtils.TruncateAt.END
                        },
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = dp(5)
                        }
                    )
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
        }
    }

    private fun updateActiveDownloadCard() {
        val activeDownload = currentDownloads.firstDownloadByStatus(DownloadStatus.RUNNING)
            ?: currentDownloads.firstDownloadByStatus(DownloadStatus.PREPARING)
            ?: currentDownloads.firstDownloadByStatus(DownloadStatus.QUEUED)
        if (activeDownload == null) {
            activeDownloadCard.visibility = View.GONE
            activeDownloadCard.setOnClickListener(null)
            return
        }

        val progress = normalizedProgress(activeDownload)
        val indeterminate = isIndeterminateDownload(activeDownload)
        activeDownloadCard.visibility = View.VISIBLE
        activeDownloadCard.setOnClickListener { showDownloadDetailsDialog(activeDownload) }
        activeDownloadTitleText.text = activeDownloadCardTitle(activeDownload.status)
        activeDownloadNameText.text = activeDownload.fileName
        activeDownloadFormatText.text =
            "${formatLabelForDetails(activeDownload)} - ${downloadStatusLabel(activeDownload.status)}"
        activeDownloadProgressText.text = progressLabel(activeDownload, indeterminate, progress)
        activeDownloadProgressBar.isIndeterminate = indeterminate
        activeDownloadProgressBar.progress = progress
        activeDownloadSpeedText.text = formatSpeedForDetails(activeDownload.speed)
        activeDownloadSizeText.text = summaryDownloadSizeText(activeDownload)
    }

    private fun List<DownloadEntity>.firstDownloadByStatus(status: DownloadStatus): DownloadEntity? {
        return firstOrNull { it.status == status }
    }

    private fun activeDownloadCardTitle(status: DownloadStatus): String {
        return when (status) {
            DownloadStatus.QUEUED -> getString(R.string.status_queued)
            DownloadStatus.PREPARING -> getString(R.string.status_preparing)
            else -> getString(R.string.downloads_active_title)
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
            DownloadStatus.QUEUED -> getString(R.string.status_queued)
            DownloadStatus.PREPARING -> getString(R.string.status_preparing_progress)
            DownloadStatus.RUNNING -> {
                val prefix = if (indeterminate) "" else "$progress% "
                prefix + getString(R.string.download_progress_unknown)
            }
            DownloadStatus.PAUSED -> if (progress > 0) {
                "$progress% ${getString(R.string.status_paused)}"
            } else {
                getString(R.string.status_paused)
            }
            DownloadStatus.COMPLETED -> "100% ${getString(R.string.status_completed)}"
            DownloadStatus.FAILED -> getString(R.string.status_failed)
            DownloadStatus.CANCELED -> getString(R.string.status_canceled)
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

    private fun downloadTypeBadgeLabel(download: DownloadEntity, formatLabel: String): String {
        val label = formatLabel.uppercase(Locale.US)
        return when {
            "MP3" in label -> "MP3"
            "MP4" in label -> "MP4"
            DownloadSourceClassifier.shouldUseHttpDownloader(download.sourceUrl) -> "HTTP"
            else -> "MIDIA"
        }
    }

    private fun renderRecentDownloads() {
        val recentUrls = loadRecentDownloadUrls()
        recentDownloadsSection.visibility = if (recentUrls.isEmpty()) View.GONE else View.VISIBLE
        clearRecentButton.visibility = if (recentUrls.isEmpty()) View.GONE else View.VISIBLE
        recentDownloadsList.removeAllViews()

        recentUrls.take(MAX_RECENT_DOWNLOAD_URLS_DISPLAYED).forEachIndexed { index, url ->
            val button = Button(this).apply {
                text = url
                setBackgroundResource(R.drawable.bg_chip)
                setTextColor(getColor(R.color.button_secondary_text))
                textSize = 12f
                isAllCaps = false
                minHeight = 0
                minimumHeight = 0
                setPadding(
                    dp(12),
                    dp(10),
                    dp(12),
                    dp(10)
                )
                maxLines = 1
                setSingleLine(true)
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                setOnClickListener {
                    homeController.setUrl(url)
                }
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (index > 0) {
                params.topMargin = dp(8)
            }
            recentDownloadsList.addView(button, params)
        }
    }

    private fun addRecentDownloadUrl(url: String) {
        val recentUrls = loadRecentDownloadUrls().toMutableList()
        recentUrls.removeAll { it == url }
        recentUrls.add(0, url)
        if (recentUrls.size > MAX_RECENT_DOWNLOAD_URLS) {
            recentUrls.subList(MAX_RECENT_DOWNLOAD_URLS, recentUrls.size).clear()
        }
        saveRecentDownloadUrls(recentUrls)
    }

    private fun loadRecentDownloadUrls(): List<String> {
        val raw = settingsPreferences.getString(PREF_RECENT_DOWNLOAD_URLS, null).orEmpty()
        if (raw.isBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    array.optString(index).trim().takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveRecentDownloadUrls(urls: List<String>) {
        val array = JSONArray()
        urls.take(MAX_RECENT_DOWNLOAD_URLS).forEach { array.put(it) }
        settingsPreferences.edit()
            .putString(PREF_RECENT_DOWNLOAD_URLS, array.toString())
            .apply()
    }

    private fun showDownloadDetailsDialog(download: DownloadEntity) {
        val contentView = buildDownloadDetailsView(download)
        val buttons = mutableListOf(
            DarkDialogButton(getString(R.string.details_close)),
            DarkDialogButton(getString(R.string.details_copy_url), primary = true) {
                copyDownloadUrl(download)
            }
        )
        if (download.status == DownloadStatus.COMPLETED) {
            buttons.add(
                DarkDialogButton(getString(R.string.open)) {
                openCompletedDownload(download)
                }
            )
        }

        showDarkContentDialog(
            title = download.fileName,
            contentView = contentView,
            buttons = buttons
        )
    }

    private fun buildDownloadDetailsView(download: DownloadEntity): View {
        val padding = (16 * resources.displayMetrics.density).toInt()
        val textView = TextView(this).apply {
            setTextIsSelectable(true)
            setTextColor(getColor(R.color.text_secondary))
            textSize = 13f
            setPadding(0, 0, 0, padding)
            text = buildDownloadDetailsText(download)
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(padding, padding, padding, padding)
            addView(
                ScrollView(context).apply {
                    addView(
                        textView,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            if (download.status == DownloadStatus.COMPLETED) {
                addView(
                    Button(context).apply {
                        text = getString(R.string.details_share)
                        setTextColor(getColor(R.color.button_secondary_text))
                        setBackgroundResource(R.drawable.bg_button_secondary)
                        setOnClickListener {
                            shareCompletedDownload(download)
                        }
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = padding
                    }
                )
            }
        }
    }

    private fun buildDownloadDetailsText(download: DownloadEntity): String {
        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
        val details = linkedMapOf(
            getString(R.string.download_detail_file_name) to download.fileName,
            getString(R.string.download_detail_status) to downloadStatusLabel(download.status),
            getString(R.string.download_detail_format) to formatLabelForDetails(download),
            getString(R.string.download_detail_source_url) to download.sourceUrl,
            getString(R.string.download_detail_downloaded) to buildDownloadSizeText(download),
            getString(R.string.download_detail_progress) to progressLabelForDetails(download),
            getString(R.string.download_detail_speed) to formatSpeedForDetails(download.speed),
            getString(R.string.download_detail_error) to (download.errorMessage?.takeIf { it.isNotBlank() } ?: getString(R.string.none)),
            getString(R.string.download_detail_final_uri) to (download.destinationUri?.takeIf { it.isNotBlank() } ?: getString(R.string.not_available)),
            getString(R.string.download_detail_created_at) to dateFormat.format(java.util.Date(download.createdAt)),
            getString(R.string.download_detail_updated_at) to dateFormat.format(java.util.Date(download.updatedAt))
        )

        return details.entries.joinToString(separator = "\n\n") { (label, value) ->
            "$label\n$value"
        }
    }

    private fun formatLabelForDetails(download: DownloadEntity): String {
        val label = YtDlpQualityOptions.labelForDownload(this, download)
        return if (label.isBlank()) getString(R.string.download_direct) else label
    }

    private fun downloadStatusLabel(status: DownloadStatus): String {
        return when (status) {
            DownloadStatus.QUEUED -> getString(R.string.status_queued)
            DownloadStatus.PREPARING -> getString(R.string.status_preparing)
            DownloadStatus.RUNNING -> getString(R.string.status_running)
            DownloadStatus.PAUSED -> getString(R.string.status_paused)
            DownloadStatus.FAILED -> getString(R.string.status_failed)
            DownloadStatus.COMPLETED -> getString(R.string.status_completed)
            DownloadStatus.CANCELED -> getString(R.string.status_canceled)
        }
    }

    private fun buildDownloadSizeText(download: DownloadEntity): String {
        val downloaded = FileSizeFormatter.formatBytes(download.downloadedBytes)
        val total = FileSizeFormatter.formatBytes(download.totalBytes)
        return "$downloaded / $total"
    }

    private fun progressLabelForDetails(download: DownloadEntity): String {
        val progress = normalizedProgress(download)
        return when (download.status) {
            DownloadStatus.QUEUED -> getString(R.string.status_queued)
            DownloadStatus.PREPARING -> getString(R.string.status_preparing_progress)
            DownloadStatus.RUNNING -> progressLabel(download, isIndeterminateDownload(download), progress)
            DownloadStatus.PAUSED -> if (progress > 0) "$progress% ${getString(R.string.status_paused)}" else getString(R.string.status_paused)
            DownloadStatus.COMPLETED -> "100% ${getString(R.string.status_completed)}"
            DownloadStatus.FAILED -> getString(R.string.status_failed)
            DownloadStatus.CANCELED -> getString(R.string.status_canceled)
        }
    }

    private fun formatSpeedForDetails(speed: Long): String {
        return if (speed > 0L) {
            FileSizeFormatter.formatSpeed(speed)
        } else {
            getString(R.string.not_available)
        }
    }

    private fun copyDownloadUrl(download: DownloadEntity) {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(
            ClipData.newPlainText(getString(R.string.details_copy_url), download.sourceUrl)
        )
        showToast(getString(R.string.url_copied))
    }

    private fun clearFinishedDownloads() {
        scope.launch {
            val removedCount = withContext(Dispatchers.IO) {
                app.container.repository.removeFinalizedDownloads().size
            }
            if (removedCount == 0) {
                showToast(getString(R.string.clear_finished_downloads_empty))
            }
        }
    }

    private fun showBrowser() {
        currentScreen = PrimaryScreen.BROWSER
        appHeader.visibility = View.VISIBLE
        mainTabBar.visibility = View.VISIBLE
        homeContainer.visibility = View.GONE
        browserContainer.visibility = View.VISIBLE
        downloadsContainer.visibility = View.GONE
        settingsContainer.visibility = View.GONE
        updateSelectedTab(browserTabButton)
    }

    private fun showSettings(scrollToDownloadLocation: Boolean = false) {
        appHeader.visibility = View.GONE
        mainTabBar.visibility = View.GONE
        homeContainer.visibility = View.GONE
        browserContainer.visibility = View.GONE
        downloadsContainer.visibility = View.GONE
        settingsContainer.visibility = View.VISIBLE
        updateDefaultQualityText()
        updateDownloadLocationText()
        updateYtDlpUpdateUiState()
        updateAutoUpdateYtDlpUiState()
        updateSelectedTab(null)
        if (scrollToDownloadLocation) {
            settingsContainer.post {
                (settingsContainer as? ScrollView)?.smoothScrollTo(0, downloadLocationCard.top)
            }
        }
    }

    private fun closeSettingsOverlay() {
        when (currentScreen) {
            PrimaryScreen.HOME -> showHome()
            PrimaryScreen.DOWNLOADS -> showDownloads()
            PrimaryScreen.BROWSER -> showBrowser()
        }
    }

    private fun DownloadEntity.matchesDownloadsFilter(filter: DownloadsFilter): Boolean {
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

    private fun DownloadEntity.matchesDownloadsSearch(query: String): Boolean {
        if (query.isBlank()) return true
        val normalizedQuery = query.lowercase(Locale.getDefault())
        val searchBlob = buildString {
            append(fileName)
            append(' ')
            append(sourceUrl)
            append(' ')
            append(downloadStatusLabel(status))
            append(' ')
            append(formatLabelForDetails(this@matchesDownloadsSearch))
        }.lowercase(Locale.getDefault())
        return searchBlob.contains(normalizedQuery)
    }

    private fun updateDownloadLocationText() {
        downloadLocationText.text = getPreferredDownloadDirectory().absolutePath
    }

    private fun showAboutDialog() {
        val versionName = runCatching {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { getString(R.string.not_available) }
        val message = buildString {
            appendLine(getString(R.string.about_app_name))
            appendLine(getString(R.string.about_app_version, versionName))
            appendLine()
            appendLine(getString(R.string.about_app_description))
            appendLine()
            append(getString(R.string.about_app_responsible_use))
        }

        showDarkMessageDialog(
            title = getString(R.string.about_dialog_title),
            message = message,
            buttons = listOf(DarkDialogButton(getString(android.R.string.ok), primary = true))
        )
    }

    private fun showDiagnosticsDialog() {
        val diagnostics = YtDlpDiagnostics.formatted(this)
        val textView = TextView(this).apply {
            text = diagnostics
            setTextIsSelectable(true)
            setTextColor(getColor(R.color.text_secondary))
            textSize = 13f
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        showDarkContentDialog(
            title = getString(R.string.diagnostics_title),
            contentView = ScrollView(this).apply {
                addView(
                    textView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            },
            buttons = listOf(
                DarkDialogButton(getString(R.string.details_close)),
                DarkDialogButton(getString(R.string.diagnostics_copy), primary = true) {
                    copyDiagnostics()
                },
                DarkDialogButton(getString(R.string.diagnostics_clear)) {
                    YtDlpDiagnostics.clear(this)
                    showToast(getString(R.string.diagnostics_cleared))
                }
            )
        )
    }

    private fun copyDiagnostics() {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(
            ClipData.newPlainText(getString(R.string.diagnostics_title), YtDlpDiagnostics.formatted(this))
        )
        showToast(getString(R.string.diagnostics_copied))
    }

    private fun getPreferredDownloadDirectory(): File {
        return getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(filesDir, "downloads")
    }

    private fun updateYtDlpManually() {
        if (ytDlpUpdateInProgress) return
        if (hasActiveDownloads) {
            showToast(getString(R.string.update_ytdlp_busy))
            updateYtDlpUpdateUiState()
            return
        }

        ytDlpUpdateInProgress = true
        ytDlpUpdateMessage = null
        updateYtDlpUpdateUiState()

        scope.launch {
            val success = withContext(Dispatchers.IO) {
                app.container.ytDlpDownloader.updateManually()
            }
            ytDlpUpdateInProgress = false
            ytDlpUpdateMessage = getString(
                if (success) R.string.update_ytdlp_success else R.string.update_ytdlp_failed
            )
            updateYtDlpUpdateUiState()
        }
    }

    private fun updateYtDlpUpdateUiState() {
        val enabled = !ytDlpUpdateInProgress && !hasActiveDownloads
        updateYtDlpButton.isEnabled = enabled
        when {
            ytDlpUpdateInProgress -> {
                ytdlpUpdateStatusText.visibility = View.VISIBLE
                ytdlpUpdateStatusText.text = getString(R.string.update_ytdlp_in_progress)
            }
            ytDlpUpdateMessage != null -> {
                ytdlpUpdateStatusText.visibility = View.VISIBLE
                ytdlpUpdateStatusText.text = ytDlpUpdateMessage
            }
            hasActiveDownloads -> {
                ytdlpUpdateStatusText.visibility = View.VISIBLE
                ytdlpUpdateStatusText.text = getString(R.string.update_ytdlp_busy)
            }
            else -> {
                ytdlpUpdateStatusText.visibility = View.GONE
            }
        }
    }

    private fun toggleAutoUpdateYtDlp() {
        val enabled = settingsPreferences.getBoolean(PREF_AUTO_UPDATE_YTDLP_ON_YOUTUBE_ERRORS, true)
        settingsPreferences.edit()
            .putBoolean(PREF_AUTO_UPDATE_YTDLP_ON_YOUTUBE_ERRORS, !enabled)
            .apply()
        updateAutoUpdateYtDlpUiState()
    }

    private fun updateAutoUpdateYtDlpUiState() {
        val enabled = settingsPreferences.getBoolean(PREF_AUTO_UPDATE_YTDLP_ON_YOUTUBE_ERRORS, true)
        autoUpdateYtDlpButton.text = getString(
            if (enabled) R.string.auto_update_ytdlp_enabled else R.string.auto_update_ytdlp_disabled
        )
    }

    private fun showDarkMessageDialog(
        title: String,
        message: String,
        buttons: List<DarkDialogButton>
    ) {
        val messageView = TextView(this).apply {
            text = message
            setTextColor(getColor(R.color.text_secondary))
            textSize = 14f
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        showDarkContentDialog(title, messageView, buttons)
    }

    private fun showDarkOptionsDialog(
        title: String,
        options: List<DarkOption>,
        selectedIndex: Int = -1,
        neutralButton: DarkDialogButton? = null,
        onSelected: (Int) -> Unit
    ) {
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        var lastSection: String? = null
        lateinit var dialog: AlertDialog
        options.forEachIndexed { index, option ->
            if (!option.section.isNullOrBlank() && option.section != lastSection) {
                lastSection = option.section
                list.addView(
                    TextView(this).apply {
                        text = option.section
                        setTextColor(getColor(R.color.brand))
                        textSize = 12f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        includeFontPadding = false
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = if (list.childCount == 0) 0 else dp(12)
                        bottomMargin = dp(8)
                    }
                )
            }
            val selected = index == selectedIndex
            list.addView(
                TextView(this).apply {
                    text = option.label
                    setTextColor(getColor(if (selected) R.color.brand else R.color.text_primary))
                    textSize = 14f
                    typeface = if (selected) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                    setBackgroundResource(if (selected) R.drawable.bg_button_secondary else R.drawable.bg_dialog_option)
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    isClickable = true
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    maxLines = 3
                    setOnClickListener {
                        dialog.dismiss()
                        onSelected(index)
                    }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(8)
                }
            )
        }

        val buttons = buildList {
            neutralButton?.let { add(it) }
            add(DarkDialogButton(getString(R.string.details_close)))
        }
        dialog = showDarkContentDialog(
            title = title,
            contentView = ScrollView(this).apply { addView(list) },
            buttons = buttons
        )
    }

    private fun showDarkContentDialog(
        title: String,
        contentView: View,
        buttons: List<DarkDialogButton>
    ): AlertDialog {
        lateinit var dialog: AlertDialog
        val padding = dp(20)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_dialog_surface)
            setPadding(padding, padding, padding, padding)
            addView(
                TextView(context).apply {
                    text = title
                    setTextColor(getColor(R.color.text_primary))
                    textSize = 20f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(14)
                }
            )
            addView(
                contentView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                LinearLayout(context).apply {
                    val stackButtons = buttons.size > 2
                    orientation = if (stackButtons) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.END
                    buttons.forEachIndexed { index, buttonSpec ->
                        addView(
                            Button(context).apply {
                                text = buttonSpec.label
                                isAllCaps = false
                                minHeight = 0
                                minWidth = 0
                                setTextColor(getColor(if (buttonSpec.primary) R.color.button_primary_text else R.color.button_secondary_text))
                                setBackgroundResource(if (buttonSpec.primary) R.drawable.bg_button_primary else R.drawable.bg_button_secondary)
                                setPadding(dp(14), 0, dp(14), 0)
                                setOnClickListener {
                                    dialog.dismiss()
                                    buttonSpec.onClick?.invoke()
                                }
                            },
                            if (stackButtons) {
                                LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    dp(42)
                                ).apply {
                                    if (index > 0) topMargin = dp(8)
                                }
                            } else {
                                LinearLayout.LayoutParams(
                                    0,
                                    dp(42),
                                    1f
                                ).apply {
                                    if (index > 0) leftMargin = dp(8)
                                }
                            }
                        )
                    }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(18)
                }
            )
        }

        dialog = AlertDialog.Builder(this)
            .setView(container)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        dialog.show()
        return dialog
    }

    private fun sectionForQualityLabel(label: String): String? {
        return when {
            label.startsWith("MP4", ignoreCase = true) -> getString(R.string.quality_section_video)
            label.startsWith("MP3", ignoreCase = true) -> getString(R.string.quality_section_audio)
            else -> null
        }
    }

    private fun updateSelectedTab(selectedTab: Button?) {
        listOf(homeTabButton, downloadsTabButton, browserTabButton).forEach { tab ->
            val isSelected = tab == selectedTab
            tab.isSelected = isSelected
            tab.setBackgroundResource(
                if (isSelected) R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected
            )
        }
    }

    private fun handleIntent(intent: Intent?) {
        val openDownloadId = intent?.getLongExtra(EXTRA_OPEN_DOWNLOAD_ID, -1L) ?: -1L
        if (openDownloadId > 0L) {
            handleOpenDownloadIntent(openDownloadId)
            return
        }

        if (intent?.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false) == true) {
            showDownloads()
            return
        }

        if (intent?.action == Intent.ACTION_SEND &&
            intent.type?.equals("text/plain", ignoreCase = true) == true
        ) {
            handleSharedText(intent)
        }
    }

    private fun handleSharedText(intent: Intent) {
        val sharedUrl = extractSharedUrl(
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        )
        if (sharedUrl == null) {
            showHome()
            showToast(getString(R.string.invalid_url))
            return
        }

        showHome()
        homeController.setUrl(sharedUrl)
        showToast(getString(R.string.shared_link_received))
    }

    private fun handleOpenDownloadIntent(downloadId: Long) {
        scope.launch {
            val download = withContext(Dispatchers.IO) {
                app.container.repository.getById(downloadId)
            }
            if (download == null) {
                showToast(getString(R.string.download_file_not_found))
                return@launch
            }
            openCompletedDownload(download)
        }
    }

    private fun extractSharedUrl(sharedText: String): String? {
        val trimmedText = sharedText.trim()
        if (UrlValidator.isValidHttpUrl(trimmedText)) {
            return trimmedText
        }

        return SHARED_URL_PATTERN.find(trimmedText)
            ?.value
            ?.trimEnd('.', ',', ';', ':', ')', ']', '}', '>')
            ?.takeIf { UrlValidator.isValidHttpUrl(it) }
    }

    private fun openYtDlpQualityPicker(
        url: String,
        homeController: HomeController? = null
    ) {
        val options = YtDlpQualityOptions.build(this@MainActivity, null)
        showDarkOptionsDialog(
            title = getString(R.string.choose_quality_title),
            options = options.map { DarkOption(it.label, sectionForQualityLabel(it.label)) }
        ) { which ->
                startQueuedDownload(
                    url = url,
                    qualitySelector = options[which].formatSelector,
                    homeController = homeController
                )
            }
    }

    private fun startQueuedDownload(
        url: String,
        qualitySelector: String?,
        homeController: HomeController? = null
    ) {
        homeController?.setLoading(true)
        scope.launch {
            val downloadId = withContext(Dispatchers.IO) {
                app.container.queue.enqueue(url, qualitySelector)
            }
            homeController?.clear()
            homeController?.setLoading(false)
            showDownloads()
            DownloadForegroundService.start(this@MainActivity, downloadId)
        }
    }

    private fun handleDownloadRequest(
        rawUrl: String,
        homeController: HomeController? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val url = rawUrl.trim()
        if (!UrlValidator.isValidHttpUrl(url)) {
            val message = getString(R.string.invalid_url)
            if (homeController != null) {
                homeController.showError(message)
            } else {
                onError?.invoke(message) ?: showToast(message)
            }
            return
        }

        addRecentDownloadUrl(url)
        if (DownloadSourceClassifier.shouldUseHttpDownloader(url)) {
            startQueuedDownload(
                url = url,
                qualitySelector = null,
                homeController = homeController
            )
        } else {
            handleYtDlpDownloadRequest(
                url = url,
                homeController = homeController
            )
        }
    }

    private fun showDefaultQualityDialog() {
        val options = defaultQualityOptions()
        val currentIndex = options.indexOfFirst {
            it.preferenceValue == selectedDefaultQualityOption().preferenceValue
        }.coerceAtLeast(0)
        showDarkOptionsDialog(
            title = getString(R.string.default_video_quality_title),
            options = options.map { DarkOption(it.label, sectionForQualityLabel(it.label)) },
            selectedIndex = currentIndex
        ) { which ->
                saveDefaultQualityOption(options[which])
                updateDefaultQualityText()
            }
    }

    private fun updateDefaultQualityText() {
        defaultQualityValueText.text = getString(
            R.string.default_quality_selected,
            selectedDefaultQualityOption().label
        )
    }

    private fun selectedDefaultQualityOption(): DefaultQualityOption {
        val savedValue = settingsPreferences.getString(
            PREF_DEFAULT_YTDLP_QUALITY,
            DEFAULT_QUALITY_ASK_VALUE
        )
        return defaultQualityOptions().firstOrNull { it.preferenceValue == savedValue }
            ?: defaultQualityOptions().first()
    }

    private fun saveDefaultQualityOption(option: DefaultQualityOption) {
        settingsPreferences.edit()
            .putString(PREF_DEFAULT_YTDLP_QUALITY, option.preferenceValue)
            .apply()
    }

    private fun defaultQualityOptions(): List<DefaultQualityOption> {
        return listOf(
            DefaultQualityOption(
                label = getString(R.string.default_quality_ask_always),
                preferenceValue = DEFAULT_QUALITY_ASK_VALUE,
                formatSelector = null
            )
        ) + YtDlpQualityOptions.build(this, null).map { option ->
            DefaultQualityOption(
                label = option.label,
                preferenceValue = option.formatSelector,
                formatSelector = option.formatSelector
            )
        }
    }

    private fun handleYtDlpDownloadRequest(
        url: String,
        homeController: HomeController? = null
    ) {
        val defaultOption = selectedDefaultQualityOption()
        if (defaultOption.formatSelector == null) {
            openYtDlpQualityPicker(
                url = url,
                homeController = homeController
            )
            return
        }
        startQueuedDownload(
            url = url,
            qualitySelector = defaultOption.formatSelector,
            homeController = homeController
        )
    }

    private fun loadBrowserPage() {
        val url = normalizeBrowserUrl(browserUrlInput.text?.toString())
        if (url == null) {
            showToast(getString(R.string.invalid_url))
            return
        }
        clearBrowserError()
        browserWebView.loadUrl(url)
    }

    private fun downloadCurrentBrowserPage() {
        val currentUrl = browserWebView.url?.takeIf { it.isNotBlank() && it != "about:blank" }
            ?: normalizeBrowserUrl(browserUrlInput.text?.toString())
        if (currentUrl == null) {
            showToast(getString(R.string.browser_no_page_loaded))
            return
        }
        handleDownloadRequest(
            rawUrl = currentUrl,
            onError = { showToast(it) }
        )
    }

    private fun normalizeBrowserUrl(rawUrl: String?): String? {
        val value = rawUrl?.trim().orEmpty()
        if (value.isBlank()) return null
        val normalized = if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
            value
        } else {
            "https://$value"
        }
        return normalized.takeIf { UrlValidator.isValidHttpUrl(it) }
    }

    private fun setupBrowserWebView() {
        CookieManager.getInstance().setAcceptCookie(false)
        browserWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            javaScriptCanOpenWindowsAutomatically = false
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(browserWebView, false)
        browserWebView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                browserProgressBar.progress = newProgress.coerceIn(0, 100)
                browserProgressBar.visibility = if (browserPageLoading && newProgress < 100) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
        }
        browserWebView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                collectDetectedMediaUrl(request?.url?.toString())
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val target = request?.url?.toString().orEmpty()
                if (target.isBlank()) return false
                updateBrowserUrlInput(target)
                return false
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                val target = url.orEmpty()
                if (target.isBlank()) return false
                updateBrowserUrlInput(target)
                return false
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                browserPageLoading = true
                clearDetectedMediaUrls()
                collectDetectedMediaUrl(url)
                clearBrowserError()
                updateBrowserUrlInput(url)
                browserProgressBar.progress = 0
                browserProgressBar.visibility = View.VISIBLE
                updateBrowserNavigationButtons()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                browserPageLoading = false
                updateBrowserUrlInput(url)
                browserProgressBar.visibility = View.GONE
                updateBrowserNavigationButtons()
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    browserPageLoading = false
                    browserProgressBar.visibility = View.GONE
                    browserErrorText.text = getString(R.string.browser_page_load_error)
                    browserErrorText.visibility = View.VISIBLE
                    updateBrowserNavigationButtons()
                }
            }
        }
        updateBrowserNavigationButtons()
        updateDetectedMediaButton()
    }

    private fun collectDetectedMediaUrl(rawUrl: String?) {
        val url = rawUrl?.trim().orEmpty()
        val candidate = buildMediaCandidate(url) ?: return
        val wasAdded = synchronized(detectedMediaLock) {
            if (detectedMediaCandidates.containsKey(candidate.url)) {
                false
            } else {
                detectedMediaCandidates[candidate.url] = candidate
                true
            }
        }
        if (wasAdded) {
            runOnUiThread { updateDetectedMediaButton() }
        }
    }

    private fun buildMediaCandidate(url: String): MediaCandidate? {
        if (!UrlValidator.isValidHttpUrl(url)) return null
        val extension = detectMediaExtension(url) ?: return null
        return MediaCandidate(
            url = url,
            displayName = buildMediaDisplayName(url),
            extension = extension,
            typeLabel = typeLabelForExtension(extension)
        )
    }

    private fun detectMediaExtension(url: String): String? {
        val lowerUrl = url.lowercase(Locale.US)
        return MEDIA_EXTENSIONS.firstOrNull { extension -> lowerUrl.contains(".$extension") }
    }

    private fun buildMediaDisplayName(url: String): String {
        val lastPathSegment = runCatching {
            Uri.parse(url).lastPathSegment.orEmpty()
        }.getOrDefault("")
        val rawName = lastPathSegment
            .substringBefore('?')
            .substringBefore('#')
            .trim()
        val decodedName = runCatching {
            Uri.decode(rawName)
        }.getOrDefault(rawName).trim()
        val displayName = decodedName.ifBlank { getString(R.string.browser_media_default_name) }
        return displayName.limitLength(MAX_MEDIA_DISPLAY_NAME_LENGTH)
    }

    private fun typeLabelForExtension(extension: String): String {
        return when (extension) {
            "mp4" -> getString(R.string.browser_media_type_video_mp4)
            "webm" -> getString(R.string.browser_media_type_video_webm)
            "m3u8" -> getString(R.string.browser_media_type_hls_streaming)
            "mp3" -> getString(R.string.browser_media_type_audio_mp3)
            "m4a" -> getString(R.string.browser_media_type_audio_m4a)
            "jpg",
            "jpeg",
            "png" -> getString(R.string.browser_media_type_image)
            "pdf" -> getString(R.string.browser_media_type_pdf)
            "zip" -> getString(R.string.browser_media_type_zip)
            else -> getString(R.string.browser_media_type_generic)
        }
    }

    private fun String.limitLength(maxLength: Int): String {
        if (length <= maxLength) return this
        return take(maxLength - ELLIPSIS.length).trimEnd() + ELLIPSIS
    }

    private fun clearDetectedMediaUrls() {
        synchronized(detectedMediaLock) {
            detectedMediaCandidates.clear()
        }
        updateDetectedMediaButton()
    }

    private fun updateDetectedMediaButton() {
        val count = synchronized(detectedMediaLock) {
            detectedMediaCandidates.size
        }
        browserDetectedMediaButton.text = getString(R.string.browser_detected_media_count, count)
        browserDetectedMediaButton.isEnabled = count > 0
    }

    private fun showDetectedMediaDialog() {
        val candidates = synchronized(detectedMediaLock) {
            detectedMediaCandidates.values.toList()
        }
        if (candidates.isEmpty()) {
            showToast(getString(R.string.browser_detected_media_empty))
            updateDetectedMediaButton()
            return
        }
        val defaultFilter = if (candidates.any { it.isVideo() || it.isAudio() }) {
            MediaFilter.VIDEO_AUDIO
        } else {
            MediaFilter.ALL
        }
        showDetectedMediaListDialog(defaultFilter)
    }

    private fun showDetectedMediaListDialog(filter: MediaFilter) {
        val candidates = synchronized(detectedMediaLock) {
            detectedMediaCandidates.values.toList()
        }.filter { candidate ->
            candidate.matchesFilter(filter)
        }.sortedMediaCandidates()
        if (candidates.isEmpty()) {
            showToast(getString(R.string.browser_detected_media_empty_category))
            return
        }
        val labels = candidates.map { candidate ->
            "${candidate.displayName}\n${candidate.typeLabel}"
        }
        showDarkOptionsDialog(
            title = filter.dialogTitle(),
            options = labels.map { DarkOption(it) },
            neutralButton = DarkDialogButton(getString(R.string.browser_detected_media_filter_button)) {
                showDetectedMediaFilterDialog()
            }
        ) { which ->
                handleDownloadRequest(
                    rawUrl = candidates[which].url,
                    onError = { showToast(it) }
                )
            }
    }

    private fun showDetectedMediaFilterDialog() {
        val filters = listOf(
            MediaFilter.ALL,
            MediaFilter.VIDEOS,
            MediaFilter.AUDIOS,
            MediaFilter.FILES
        )
        val labels = filters.map { it.label() }
        showDarkOptionsDialog(
            title = getString(R.string.browser_detected_media_filter_title),
            options = labels.map { DarkOption(it) }
        ) { which ->
                showDetectedMediaListDialog(filters[which])
            }
    }

    private fun MediaCandidate.matchesFilter(filter: MediaFilter): Boolean {
        return when (filter) {
            MediaFilter.ALL -> true
            MediaFilter.VIDEO_AUDIO -> isVideo() || isAudio()
            MediaFilter.VIDEOS -> isVideo()
            MediaFilter.AUDIOS -> isAudio()
            MediaFilter.FILES -> isFileOrOther()
        }
    }

    private fun MediaCandidate.isVideo(): Boolean {
        return extension in VIDEO_EXTENSIONS
    }

    private fun MediaCandidate.isAudio(): Boolean {
        return extension in AUDIO_EXTENSIONS
    }

    private fun MediaCandidate.isFileOrOther(): Boolean {
        return extension in FILE_EXTENSIONS
    }

    private fun MediaFilter.label(): String {
        return when (this) {
            MediaFilter.ALL -> getString(R.string.browser_media_filter_all)
            MediaFilter.VIDEO_AUDIO -> getString(R.string.browser_media_filter_video_audio)
            MediaFilter.VIDEOS -> getString(R.string.browser_media_filter_videos)
            MediaFilter.AUDIOS -> getString(R.string.browser_media_filter_audios)
            MediaFilter.FILES -> getString(R.string.browser_media_filter_files)
        }
    }

    private fun MediaFilter.dialogTitle(): String {
        return when (this) {
            MediaFilter.VIDEO_AUDIO -> getString(R.string.browser_media_filter_video_audio)
            else -> label()
        }
    }

    private fun List<MediaCandidate>.sortedMediaCandidates(): List<MediaCandidate> {
        return withIndex()
            .sortedWith(
                compareBy<IndexedValue<MediaCandidate>> { mediaSortRank(it.value.extension) }
                    .thenBy { it.index }
            )
            .map { it.value }
    }

    private fun mediaSortRank(extension: String): Int {
        return when (extension) {
            "mp4",
            "webm",
            "m3u8" -> 0
            "mp3",
            "m4a" -> 1
            else -> 2
        }
    }

    private fun updateBrowserUrlInput(url: String?) {
        val target = url.orEmpty()
        if (target.isBlank()) return
        browserUrlInput.setText(target)
        browserUrlInput.setSelection(target.length)
    }

    private fun clearBrowserError() {
        browserErrorText.visibility = View.GONE
    }

    private fun updateBrowserNavigationButtons() {
        browserBackButton.isEnabled = browserWebView.canGoBack()
        browserForwardButton.isEnabled = browserWebView.canGoForward()
        browserReloadButton.isEnabled = browserWebView.url?.let { it.isNotBlank() && it != "about:blank" } == true
    }

    override fun onBackPressed() {
        if (settingsContainer.visibility == View.VISIBLE) {
            closeSettingsOverlay()
            return
        }
        if (browserContainer.visibility == View.VISIBLE && browserWebView.canGoBack()) {
            browserWebView.goBack()
            return
        }
        super.onBackPressed()
    }

    private fun openCompletedDownload(download: DownloadEntity) {
        if (download.status != DownloadStatus.COMPLETED) return

        val openUri = try {
            resolveOpenUri(download)
        } catch (exception: IllegalArgumentException) {
            null
        }

        if (openUri == null) {
            showToast(getString(R.string.download_file_not_found))
            return
        }

        val mimeType = normalizeMimeType(download.mimeType)
            ?: contentResolver.getType(openUri)
            ?: inferMimeType(download.fileName)
            ?: "*/*"

        val viewIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(openUri, mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        if (packageManager.queryIntentActivities(viewIntent, PackageManager.MATCH_DEFAULT_ONLY).isEmpty()) {
            showToast(getString(R.string.download_no_app_found))
            return
        }

        try {
            startActivity(Intent.createChooser(viewIntent, getString(R.string.open_file_chooser)))
        } catch (exception: ActivityNotFoundException) {
            showToast(getString(R.string.download_no_app_found))
        } catch (exception: RuntimeException) {
            showToast(getString(R.string.download_open_error))
        }
    }

    private fun shareCompletedDownload(download: DownloadEntity) {
        if (download.status != DownloadStatus.COMPLETED) return

        val shareUri = try {
            resolveOpenUri(download)
        } catch (exception: IllegalArgumentException) {
            null
        }

        if (shareUri == null) {
            showToast(getString(R.string.share_file_not_found))
            return
        }

        val mimeType = normalizeMimeType(download.mimeType)
            ?: contentResolver.getType(shareUri)
            ?: inferMimeType(download.fileName)
            ?: "application/octet-stream"

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, shareUri)
            clipData = ClipData.newUri(contentResolver, download.fileName, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        if (packageManager.queryIntentActivities(sendIntent, PackageManager.MATCH_DEFAULT_ONLY).isEmpty()) {
            showToast(getString(R.string.share_no_app_found))
            return
        }

        try {
            startActivity(Intent.createChooser(sendIntent, getString(R.string.share_file_chooser)))
        } catch (exception: ActivityNotFoundException) {
            showToast(getString(R.string.share_no_app_found))
        } catch (exception: RuntimeException) {
            showToast(getString(R.string.share_open_error))
        }
    }

    private fun resolveOpenUri(download: DownloadEntity): Uri? {
        val destination = download.destinationUri?.takeIf { it.isNotBlank() } ?: return null
        val destinationUri = Uri.parse(destination)

        return when (destinationUri.scheme) {
            "content" -> destinationUri
            "file" -> {
                val file = File(destinationUri.path ?: return null)
                if (!file.exists()) return null
                FileProvider.getUriForFile(this, fileProviderAuthority(), file)
            }
            null -> {
                val file = File(destination)
                if (!file.exists()) return null
                FileProvider.getUriForFile(this, fileProviderAuthority(), file)
            }
            else -> null
        }
    }

    private fun fileProviderAuthority(): String = "$packageName.fileprovider"

    private fun normalizeMimeType(mimeType: String?): String? {
        return mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.takeIf { it.contains('/') }
    }

    private fun inferMimeType(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.US)
            .takeIf { it.isNotBlank() }
            ?: return null
        return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class MediaCandidate(
        val url: String,
        val displayName: String,
        val extension: String,
        val typeLabel: String
    )

    private data class DefaultQualityOption(
        val label: String,
        val preferenceValue: String,
        val formatSelector: String?
    )

    private enum class MediaFilter {
        ALL,
        VIDEO_AUDIO,
        VIDEOS,
        AUDIOS,
        FILES
    }

    private enum class DownloadsFilter {
        ALL,
        ACTIVE,
        PAUSED,
        COMPLETED,
        FAILED
    }

    private enum class PrimaryScreen {
        HOME,
        DOWNLOADS,
        BROWSER
    }

    private data class DarkDialogButton(
        val label: String,
        val primary: Boolean = false,
        val onClick: (() -> Unit)? = null
    )

    private data class DarkOption(
        val label: String,
        val section: String? = null
    )

    companion object {
        const val EXTRA_OPEN_DOWNLOADS = "com.androiddownload.extra.OPEN_DOWNLOADS"
        const val EXTRA_OPEN_DOWNLOAD_ID = "com.androiddownload.extra.OPEN_DOWNLOAD_ID"
        private const val SETTINGS_PREFS_NAME = "aio_downloader_settings"
        private const val PREF_AUTO_UPDATE_YTDLP_ON_YOUTUBE_ERRORS = "auto_update_ytdlp_on_youtube_errors"
        private const val PREF_DEFAULT_YTDLP_QUALITY = "default_ytdlp_quality"
        private const val PREF_RECENT_DOWNLOAD_URLS = "recent_download_urls"
        private const val DEFAULT_QUALITY_ASK_VALUE = "ask"
        private const val MAX_MEDIA_DISPLAY_NAME_LENGTH = 56
        private const val MAX_HOME_RECENT_DOWNLOADS_DISPLAYED = 4
        private const val MAX_RECENT_DOWNLOAD_URLS = 10
        private const val MAX_RECENT_DOWNLOAD_URLS_DISPLAYED = 5
        private const val ELLIPSIS = "..."
        private val SHARED_URL_PATTERN = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
        private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "m3u8")
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a")
        private val FILE_EXTENSIONS = setOf("pdf", "zip", "jpg", "jpeg", "png")
        private val MEDIA_EXTENSIONS = listOf(
            "mp4",
            "webm",
            "m3u8",
            "mp3",
            "m4a",
            "jpg",
            "jpeg",
            "png",
            "pdf",
            "zip"
        )
    }
}
