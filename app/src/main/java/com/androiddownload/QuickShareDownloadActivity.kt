package com.androiddownload

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.androiddownload.core.utils.DownloadSourceClassifier
import com.androiddownload.core.utils.SharedTextUrlExtractor
import com.androiddownload.core.utils.YtDlpQualityOptions
import com.androiddownload.download.service.DownloadForegroundService
import com.androiddownload.ui.downloads.QualityOptionUi
import com.androiddownload.ui.downloads.QuickDownloadSheetController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuickShareDownloadActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val quickDownloadSheetController: QuickDownloadSheetController
        get() = QuickDownloadSheetController(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShareIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun handleShareIntent(intent: Intent?) {
        val sharedText = intent?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)
            .orEmpty()
        val sharedUrl = SharedTextUrlExtractor.extract(sharedText)
        if (sharedUrl == null) {
            Toast.makeText(this, getString(R.string.invalid_url), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (DownloadSourceClassifier.shouldUseHttpDownloader(sharedUrl)) {
            Toast.makeText(this, "Abra o app para baixar links diretos.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        quickDownloadSheetController.show(
            url = sharedUrl,
            options = downloadQualityOptions(),
            onCanceled = { finish() }
        ) { option ->
            startSharedDownload(
                url = sharedUrl,
                qualitySelector = option.formatSelector
            )
        }
    }

    private fun downloadQualityOptions(): List<QualityOptionUi> {
        return YtDlpQualityOptions.build(this, null).map { option ->
            QualityOptionUi(
                label = option.label,
                preferenceValue = option.formatSelector,
                formatSelector = option.formatSelector
            )
        }
    }

    private fun startSharedDownload(url: String, qualitySelector: String?) {
        scope.launch {
            try {
                val downloadId = withContext(Dispatchers.IO) {
                    app.container.queue.enqueue(url, qualitySelector)
                }
                DownloadForegroundService.start(this@QuickShareDownloadActivity, downloadId)
                Toast.makeText(
                    this@QuickShareDownloadActivity,
                    getString(R.string.shared_link_received),
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Toast.makeText(
                    this@QuickShareDownloadActivity,
                    getString(R.string.download_error_generic_short),
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    private val app: AndroidDownloadApp
        get() = application as AndroidDownloadApp
}
