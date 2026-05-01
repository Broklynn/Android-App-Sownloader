package com.androiddownload

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import com.androiddownload.core.utils.UrlValidator
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

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var homeContainer: View
    private lateinit var downloadsContainer: View
    private lateinit var emptyDownloadsText: TextView
    private lateinit var adapter: DownloadsAdapter

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
        downloadsContainer = findViewById(R.id.downloadsContainer)
        emptyDownloadsText = findViewById(R.id.emptyDownloadsText)

        adapter = DownloadsAdapter(this) { download ->
            DownloadForegroundService.cancel(this, download.id)
        }
        findViewById<ListView>(R.id.downloadsList).adapter = adapter

        findViewById<Button>(R.id.homeTabButton).setOnClickListener { showHome() }
        findViewById<Button>(R.id.downloadsTabButton).setOnClickListener { showDownloads() }

        homeController.onDownloadClick = onDownloadClick@{ rawUrl ->
            val url = rawUrl.trim()
            if (!UrlValidator.isValidHttpUrl(url)) {
                homeController.showError(getString(R.string.invalid_url))
                return@onDownloadClick
            }

            homeController.setLoading(true)
            scope.launch {
                val downloadId = withContext(Dispatchers.IO) {
                    app.container.queue.enqueue(url)
                }
                homeController.clear()
                homeController.setLoading(false)
                showDownloads()
                DownloadForegroundService.start(this@MainActivity, downloadId)
            }
        }

        scope.launch {
            app.container.repository.observeDownloads().collectLatest { downloads ->
                adapter.submitList(downloads)
                emptyDownloadsText.visibility = if (downloads.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun showHome() {
        homeContainer.visibility = View.VISIBLE
        downloadsContainer.visibility = View.GONE
    }

    private fun showDownloads() {
        homeContainer.visibility = View.GONE
        downloadsContainer.visibility = View.VISIBLE
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
}
