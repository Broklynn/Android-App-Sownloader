package com.androiddownload

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.DialogInterface
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
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
import com.androiddownload.download.service.DownloadForegroundService
import com.androiddownload.ui.downloads.DownloadsAdapter
import com.androiddownload.ui.home.HomeController
import com.yausername.youtubedl_android.YoutubeDL
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

    private lateinit var homeContainer: View
    private lateinit var browserContainer: View
    private lateinit var downloadsContainer: View
    private lateinit var settingsContainer: View
    private lateinit var emptyDownloadsText: TextView
    private lateinit var clearFinishedButton: Button
    private lateinit var downloadsSearchInput: EditText
    private lateinit var recentDownloadsSection: LinearLayout
    private lateinit var clearRecentButton: Button
    private lateinit var recentDownloadsList: LinearLayout
    private lateinit var downloadsFilterAllButton: Button
    private lateinit var downloadsFilterActiveButton: Button
    private lateinit var downloadsFilterPausedButton: Button
    private lateinit var downloadsFilterCompletedButton: Button
    private lateinit var downloadsFilterFailedButton: Button
    private lateinit var downloadLocationText: TextView
    private lateinit var ytdlpUpdateStatusText: TextView
    private lateinit var updateYtDlpButton: Button
    private lateinit var aboutAppButton: Button
    private lateinit var homeController: HomeController
    private lateinit var adapter: DownloadsAdapter
    private lateinit var homeTabButton: Button
    private lateinit var downloadsTabButton: Button
    private lateinit var browserTabButton: Button
    private lateinit var settingsTabButton: Button
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

        homeContainer = findViewById(R.id.homeContainer)
        browserContainer = findViewById(R.id.browserContainer)
        downloadsContainer = findViewById(R.id.downloadsContainer)
        settingsContainer = findViewById(R.id.settingsContainer)
        emptyDownloadsText = findViewById(R.id.emptyDownloadsText)
        clearFinishedButton = findViewById(R.id.clearFinishedButton)
        downloadsSearchInput = findViewById(R.id.downloadsSearchInput)
        recentDownloadsSection = findViewById(R.id.recentDownloadsSection)
        clearRecentButton = findViewById(R.id.clearRecentButton)
        recentDownloadsList = findViewById(R.id.recentDownloadsList)
        downloadsFilterAllButton = findViewById(R.id.downloadsFilterAllButton)
        downloadsFilterActiveButton = findViewById(R.id.downloadsFilterActiveButton)
        downloadsFilterPausedButton = findViewById(R.id.downloadsFilterPausedButton)
        downloadsFilterCompletedButton = findViewById(R.id.downloadsFilterCompletedButton)
        downloadsFilterFailedButton = findViewById(R.id.downloadsFilterFailedButton)
        downloadLocationText = findViewById(R.id.downloadLocationText)
        ytdlpUpdateStatusText = findViewById(R.id.ytdlpUpdateStatusText)
        updateYtDlpButton = findViewById(R.id.updateYtDlpButton)
        aboutAppButton = findViewById(R.id.aboutAppButton)
        homeTabButton = findViewById(R.id.homeTabButton)
        downloadsTabButton = findViewById(R.id.downloadsTabButton)
        browserTabButton = findViewById(R.id.browserTabButton)
        settingsTabButton = findViewById(R.id.settingsTabButton)
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
        settingsTabButton.setOnClickListener { showSettings() }
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
                hasActiveDownloads = downloads.any {
                    it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.PREPARING
                }
                updateYtDlpUpdateUiState()
            }
        }

        updateDefaultQualityText()
        updateDownloadLocationText()
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
        homeContainer.visibility = View.VISIBLE
        browserContainer.visibility = View.GONE
        downloadsContainer.visibility = View.GONE
        settingsContainer.visibility = View.GONE
        renderRecentDownloads()
        updateSelectedTab(homeTabButton)
    }

    private fun showDownloads() {
        homeContainer.visibility = View.GONE
        browserContainer.visibility = View.GONE
        downloadsContainer.visibility = View.VISIBLE
        settingsContainer.visibility = View.GONE
        setDownloadsFilter(DownloadsFilter.ALL, refreshOnly = true)
        setDownloadsSearchQuery("", refreshOnly = true)
        updateSelectedTab(downloadsTabButton)
    }

    private fun showClearFinishedDownloadsDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.clear_finished_downloads_title))
            .setMessage(getString(R.string.clear_finished_downloads_message))
            .setPositiveButton(android.R.string.ok) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                clearFinishedDownloads()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
        val selectedColor = R.color.text_primary
        val unselectedColor = R.color.text_secondary
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

    private fun renderRecentDownloads() {
        val recentUrls = loadRecentDownloadUrls()
        recentDownloadsSection.visibility = if (recentUrls.isEmpty()) View.GONE else View.VISIBLE
        clearRecentButton.visibility = if (recentUrls.isEmpty()) View.GONE else View.VISIBLE
        recentDownloadsList.removeAllViews()

        recentUrls.take(MAX_RECENT_DOWNLOAD_URLS_DISPLAYED).forEachIndexed { index, url ->
            val button = Button(this).apply {
                text = url
                setBackgroundResource(R.drawable.bg_button_secondary)
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
        val dialog = AlertDialog.Builder(this)
            .setTitle(download.fileName)
            .setView(contentView)
            .setPositiveButton(R.string.details_copy_url) { dialog: DialogInterface, _: Int ->
                copyDownloadUrl(download)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.details_close, null)

        if (download.status == DownloadStatus.COMPLETED) {
            dialog.setNeutralButton(getString(R.string.open)) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                openCompletedDownload(download)
            }
        }

        dialog.show()
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
            "Nome do arquivo" to download.fileName,
            "Status" to downloadStatusLabel(download.status),
            "Formato/qualidade" to formatLabelForDetails(download),
            "URL original" to download.sourceUrl,
            "Baixado" to buildDownloadSizeText(download),
            "Progresso" to progressLabelForDetails(download),
            "Velocidade" to formatSpeedForDetails(download.speed),
            "Erro" to (download.errorMessage?.takeIf { it.isNotBlank() } ?: "Nenhum"),
            "URI final" to (download.destinationUri?.takeIf { it.isNotBlank() } ?: "N/D"),
            "Criado em" to dateFormat.format(java.util.Date(download.createdAt)),
            "Atualizado em" to dateFormat.format(java.util.Date(download.updatedAt))
        )

        return details.entries.joinToString(separator = "\n\n") { (label, value) ->
            "$label\n$value"
        }
    }

    private fun formatLabelForDetails(download: DownloadEntity): String {
        val label = YtDlpQualityOptions.labelForDownload(download)
        return if (label.isBlank()) "HTTP" else label
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
        return if (download.totalBytes > 0L) {
            "${download.progress.coerceIn(0, 100)}%"
        } else {
            getString(R.string.download_progress_unknown)
        }
    }

    private fun formatSpeedForDetails(speed: Long): String {
        return if (speed > 0L) {
            FileSizeFormatter.formatSpeed(speed)
        } else {
            "N/D"
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
        homeContainer.visibility = View.GONE
        browserContainer.visibility = View.VISIBLE
        downloadsContainer.visibility = View.GONE
        settingsContainer.visibility = View.GONE
        updateSelectedTab(browserTabButton)
    }

    private fun showSettings() {
        homeContainer.visibility = View.GONE
        browserContainer.visibility = View.GONE
        downloadsContainer.visibility = View.GONE
        settingsContainer.visibility = View.VISIBLE
        updateDefaultQualityText()
        updateDownloadLocationText()
        updateYtDlpUpdateUiState()
        updateSelectedTab(settingsTabButton)
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
        }.getOrNull().orEmpty().ifBlank { "N/D" }
        val message = buildString {
            appendLine(getString(R.string.about_app_name))
            appendLine(getString(R.string.about_app_version, versionName))
            appendLine()
            appendLine(getString(R.string.about_app_description))
            appendLine()
            append(getString(R.string.about_app_responsible_use))
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.about_dialog_title))
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
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

    private fun updateSelectedTab(selectedTab: Button) {
        listOf(homeTabButton, downloadsTabButton, browserTabButton, settingsTabButton).forEach { tab ->
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
        homeController?.setLoading(true)
        scope.launch {
            val videoInfo = withContext(Dispatchers.IO) {
                runCatching { YoutubeDL.getInstance().getInfo(url) }.getOrNull()
            }
            val options = YtDlpQualityOptions.build(this@MainActivity, videoInfo)
            val dialogTitle = YtDlpQualityOptions.displayTitle(videoInfo)

            AlertDialog.Builder(this@MainActivity)
                .setTitle(dialogTitle)
                .setItems(options.map { it.label }.toTypedArray()) { dialog: DialogInterface, which: Int ->
                    dialog.dismiss()
                    startQueuedDownload(
                        url = url,
                        qualitySelector = options[which].formatSelector,
                        homeController = homeController
                    )
                }
                .setOnCancelListener {
                    homeController?.setLoading(false)
                }
                .show()
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
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.default_video_quality_title))
            .setSingleChoiceItems(
                options.map { it.label }.toTypedArray(),
                currentIndex
            ) { dialog: DialogInterface, which: Int ->
                saveDefaultQualityOption(options[which])
                updateDefaultQualityText()
                dialog.dismiss()
            }
            .show()
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
        val displayName = decodedName.ifBlank { DEFAULT_MEDIA_DISPLAY_NAME }
        return displayName.limitLength(MAX_MEDIA_DISPLAY_NAME_LENGTH)
    }

    private fun typeLabelForExtension(extension: String): String {
        return when (extension) {
            "mp4" -> "V\u00eddeo MP4"
            "webm" -> "V\u00eddeo WEBM"
            "m3u8" -> "HLS / Streaming"
            "mp3" -> "\u00c1udio MP3"
            "m4a" -> "\u00c1udio M4A"
            "jpg",
            "jpeg",
            "png" -> "Imagem"
            "pdf" -> "PDF"
            "zip" -> "ZIP"
            else -> "M\u00eddia"
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
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(filter.dialogTitle())
            .setItems(labels) { dialog: DialogInterface, which: Int ->
                dialog.dismiss()
                handleDownloadRequest(
                    rawUrl = candidates[which].url,
                    onError = { showToast(it) }
                )
            }
            .setNeutralButton(getString(R.string.browser_detected_media_filter_button)) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
                showDetectedMediaFilterDialog()
            }
            .show()
    }

    private fun showDetectedMediaFilterDialog() {
        val filters = listOf(
            MediaFilter.ALL,
            MediaFilter.VIDEOS,
            MediaFilter.AUDIOS,
            MediaFilter.FILES
        )
        val labels = filters.map { it.label() }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.browser_detected_media_filter_title))
            .setItems(labels) { dialog: DialogInterface, which: Int ->
                dialog.dismiss()
                showDetectedMediaListDialog(filters[which])
            }
            .show()
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

    companion object {
        const val EXTRA_OPEN_DOWNLOADS = "com.androiddownload.extra.OPEN_DOWNLOADS"
        const val EXTRA_OPEN_DOWNLOAD_ID = "com.androiddownload.extra.OPEN_DOWNLOAD_ID"
        private const val SETTINGS_PREFS_NAME = "aio_downloader_settings"
        private const val PREF_DEFAULT_YTDLP_QUALITY = "default_ytdlp_quality"
        private const val PREF_RECENT_DOWNLOAD_URLS = "recent_download_urls"
        private const val DEFAULT_QUALITY_ASK_VALUE = "ask"
        private const val DEFAULT_MEDIA_DISPLAY_NAME = "M\u00eddia detectada"
        private const val MAX_MEDIA_DISPLAY_NAME_LENGTH = 56
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
