package com.androiddownload.ui.home

import com.androiddownload.download.request.DownloadRequestDecision
import com.androiddownload.download.request.DownloadRequestPlanner
import com.androiddownload.ui.downloads.QualityOptionUi

class HomeDownloadRequestController(
    private val selectedDefaultQualityProvider: () -> QualityOptionUi,
    private val showInvalidUrl: (String) -> Unit,
    private val invalidUrlMessageProvider: () -> String,
    private val addRecentDownloadUrl: (String) -> Unit,
    private val openQualityPicker: (url: String, homeController: HomeController?) -> Unit,
    private val startDownload: (url: String, qualitySelector: String?, homeController: HomeController?) -> Unit,
    private val planner: DownloadRequestPlanner = DownloadRequestPlanner()
) {
    fun handleDownloadRequest(
        rawUrl: String,
        homeController: HomeController? = null
    ) {
        val selectedDefaultQuality = selectedDefaultQualityProvider()
        when (
            val decision = planner.plan(
                rawUrl = rawUrl,
                defaultQualityPreferenceValue = selectedDefaultQuality.preferenceValue,
                defaultQualitySelector = selectedDefaultQuality.formatSelector
            )
        ) {
            is DownloadRequestDecision.InvalidUrl -> {
                showInvalidUrl(decision.message ?: invalidUrlMessageProvider())
            }
            is DownloadRequestDecision.DirectDownload -> {
                addRecentDownloadUrl(decision.url)
                startDownload(decision.url, null, homeController)
            }
            is DownloadRequestDecision.YtDlpAskQuality -> {
                addRecentDownloadUrl(decision.url)
                openQualityPicker(decision.url, homeController)
            }
            is DownloadRequestDecision.YtDlpFixedQuality -> {
                addRecentDownloadUrl(decision.url)
                startDownload(decision.url, decision.qualitySelector, homeController)
            }
        }
    }
}
