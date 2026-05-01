package com.androiddownload

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
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
import java.io.File
import java.util.Locale
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var homeContainer: View
    private lateinit var browserContainer: View
    private lateinit var downloadsContainer: View
    private lateinit var emptyDownloadsText: TextView
    private lateinit var adapter: DownloadsAdapter
    private lateinit var browserUrlInput: EditText
    private lateinit var browserGoButton: Button
    private lateinit var browserDownloadButton: Button
    private lateinit var browserWebView: WebView
    private val app: AndroidDownloadApp
        get() = application as AndroidDownloadApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestNotificationPermission()

        val app = application as AndroidDownloadApp
        val homeController = HomeController(
            urlInput = findViewById(R.id.urlInput),
            downloadButton = findViewById(R.id.downloadButton),
            errorText = findViewById(R.id.urlErrorText)
        )

        homeContainer = findViewById(R.id.homeContainer)
        browserContainer = findViewById(R.id.browserContainer)
        downloadsContainer = findViewById(R.id.downloadsContainer)
        emptyDownloadsText = findViewById(R.id.emptyDownloadsText)
        browserUrlInput = findViewById(R.id.browserUrlInput)
        browserGoButton = findViewById(R.id.browserGoButton)
        browserDownloadButton = findViewById(R.id.browserDownloadButton)
        browserWebView = findViewById(R.id.browserWebView)
        setupBrowserWebView()

        adapter = DownloadsAdapter(
            context = this,
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
            }
        )
        findViewById<ListView>(R.id.downloadsList).adapter = adapter

        findViewById<Button>(R.id.homeTabButton).setOnClickListener { showHome() }
        findViewById<Button>(R.id.downloadsTabButton).setOnClickListener { showDownloads() }
        findViewById<Button>(R.id.browserTabButton).setOnClickListener { showBrowser() }
        browserGoButton.setOnClickListener { loadBrowserPage() }
        browserDownloadButton.setOnClickListener { downloadCurrentBrowserPage() }

        homeController.onDownloadClick = onDownloadClick@{ rawUrl ->
            handleDownloadRequest(
                rawUrl = rawUrl,
                onError = homeController::showError,
                homeController = homeController
            )
        }

        scope.launch {
            app.container.repository.observeDownloads().collectLatest { downloads ->
                adapter.submitList(downloads)
                emptyDownloadsText.visibility = if (downloads.isEmpty()) View.VISIBLE else View.GONE
            }
        }

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
    }

    private fun showDownloads() {
        homeContainer.visibility = View.GONE
        browserContainer.visibility = View.GONE
        downloadsContainer.visibility = View.VISIBLE
    }

    private fun showBrowser() {
        homeContainer.visibility = View.GONE
        browserContainer.visibility = View.VISIBLE
        downloadsContainer.visibility = View.GONE
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false) == true) {
            showDownloads()
        }
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

        if (DownloadSourceClassifier.shouldUseHttpDownloader(url)) {
            startQueuedDownload(
                url = url,
                qualitySelector = null,
                homeController = homeController
            )
        } else {
            openYtDlpQualityPicker(
                url = url,
                homeController = homeController
            )
        }
    }

    private fun loadBrowserPage() {
        val url = normalizeBrowserUrl(browserUrlInput.text?.toString())
        if (url == null) {
            showToast(getString(R.string.invalid_url))
            return
        }
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
        browserWebView.webChromeClient = object : WebChromeClient() {}
        browserWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val target = request?.url?.toString().orEmpty()
                if (target.isBlank()) return false
                browserUrlInput.setText(target)
                browserUrlInput.setSelection(target.length)
                return false
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                val target = url.orEmpty()
                if (target.isBlank()) return false
                browserUrlInput.setText(target)
                browserUrlInput.setSelection(target.length)
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                val target = url.orEmpty()
                if (target.isNotBlank()) {
                    browserUrlInput.setText(target)
                    browserUrlInput.setSelection(target.length)
                }
            }
        }
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

    companion object {
        const val EXTRA_OPEN_DOWNLOADS = "com.androiddownload.extra.OPEN_DOWNLOADS"
    }
}
