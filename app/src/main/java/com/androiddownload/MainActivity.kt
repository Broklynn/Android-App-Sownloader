package com.androiddownload

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.Button
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.media3.ui.PlayerView
import com.androiddownload.core.files.FileActionsController
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import com.androiddownload.core.preferences.DefaultQualityPreferences
import com.androiddownload.core.preferences.RecentDownloadsStore
import com.androiddownload.core.preferences.SettingsPreferencesStore
import com.androiddownload.core.utils.DownloadSourceClassifier
import com.androiddownload.core.utils.SharedTextUrlExtractor
import com.androiddownload.download.service.DownloadForegroundService
import com.androiddownload.ui.downloads.ClearFinishedDownloadsController
import com.androiddownload.ui.downloads.DownloadDetailsDialogController
import com.androiddownload.ui.downloads.DownloadsController
import com.androiddownload.ui.downloads.DownloadsFilter
import com.androiddownload.ui.downloads.DownloadOpenRouter
import com.androiddownload.ui.downloads.DownloadTextProvider
import com.androiddownload.ui.home.HomeDownloadRequestController
import com.androiddownload.ui.home.HomeController
import com.androiddownload.ui.home.HomeScreenController
import com.androiddownload.ui.navigation.MainHeaderController
import com.androiddownload.ui.navigation.MainNavigationController
import com.androiddownload.ui.navigation.PrimaryScreen
import com.androiddownload.ui.downloads.QualityDialogController
import com.androiddownload.ui.downloads.QualityOptionUi
import com.androiddownload.ui.downloads.QuickDownloadSheetController
import com.androiddownload.ui.player.ActiveVideoMode
import com.androiddownload.ui.player.AudioPlaybackController
import com.androiddownload.ui.player.FullscreenChromeController
import com.androiddownload.ui.player.FullscreenControlsController
import com.androiddownload.ui.player.FullscreenGestureController
import com.androiddownload.ui.player.FullscreenOverlayController
import com.androiddownload.ui.player.FullscreenSeekController
import com.androiddownload.ui.player.Media3VideoPlaybackController
import com.androiddownload.ui.player.PlayerAdjacentNavigator
import com.androiddownload.ui.player.PlayerCategory
import com.androiddownload.ui.player.PlayerCompletionAction
import com.androiddownload.ui.player.PlayerCompletionResolver
import com.androiddownload.ui.player.PlayerControlsController
import com.androiddownload.ui.player.PlayerListController
import com.androiddownload.ui.player.PlayerListRenderer
import com.androiddownload.ui.player.PlayerMediaLabelResolver
import com.androiddownload.ui.player.PlayerNowPlayingTextFormatter
import com.androiddownload.ui.player.PlayerPlaybackFailureAction
import com.androiddownload.ui.player.PlayerPlaybackFailureResolver
import com.androiddownload.ui.player.PlayerProgressCalculator
import com.androiddownload.ui.player.PlaybackUriResolver
import com.androiddownload.ui.settings.DiagnosticsController
import com.androiddownload.ui.settings.DownloadLocationController
import com.androiddownload.ui.settings.SettingsController
import com.androiddownload.ui.settings.SettingsScreenController
import com.androiddownload.ui.settings.YtDlpUpdateController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : Activity() {
    private enum class ActivePlaybackSource {
        AUDIO,
        INLINE_VIDEO,
        FULLSCREEN_VIDEO,
        NONE
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var appHeader: View
    private lateinit var mainTabBar: View
    private lateinit var homeContainer: View
    private lateinit var playerContainer: View
    private lateinit var downloadsContainer: View
    private lateinit var settingsMenuButton: ImageButton
    private lateinit var headerSearchButton: ImageButton
    private lateinit var clearFinishedButton: Button
    private lateinit var homeScreenController: HomeScreenController
    private lateinit var downloadsController: DownloadsController
    private lateinit var downloadTextProvider: DownloadTextProvider
    private lateinit var clearFinishedDownloadsController: ClearFinishedDownloadsController
    private lateinit var settingsController: SettingsController
    private lateinit var settingsScreenController: SettingsScreenController
    private lateinit var downloadLocationController: DownloadLocationController
    private lateinit var ytDlpUpdateController: YtDlpUpdateController
    private lateinit var mainNavigationController: MainNavigationController
    private lateinit var homeTabButton: Button
    private lateinit var downloadsTabButton: Button
    private lateinit var playerTabButton: Button
    private lateinit var defaultQualityButton: Button
    private lateinit var playerMusicChip: Button
    private lateinit var playerVideoChip: Button
    private lateinit var playerVideoFrame: View
    private lateinit var playerArtworkPlaceholder: TextView
    private lateinit var media3PlayerVideoView: PlayerView
    private lateinit var playerNowPlayingTitle: TextView
    private lateinit var playerNowPlayingSubtitle: TextView
    private lateinit var playerNowPlayingMetaText: TextView
    private lateinit var playerSeekBar: SeekBar
    private lateinit var playerCurrentTimeText: TextView
    private lateinit var playerDurationText: TextView
    private lateinit var playerPreviousButton: Button
    private lateinit var playerPlayPauseButton: Button
    private lateinit var playerNextButton: Button
    private lateinit var playerFullscreenButton: ImageButton
    private lateinit var playerControlsController: PlayerControlsController
    private lateinit var fullscreenControlsController: FullscreenControlsController
    private lateinit var playerListRenderer: PlayerListRenderer
    private lateinit var videoFullscreenOverlay: View
    private lateinit var videoFullscreenControls: View
    private lateinit var videoFullscreenCloseButton: ImageButton
    private lateinit var media3VideoFullscreenView: PlayerView
    private lateinit var videoFullscreenSeekBar: SeekBar
    private lateinit var videoFullscreenCurrentTimeText: TextView
    private lateinit var videoFullscreenDurationText: TextView
    private lateinit var videoFullscreenPlayPauseButton: ImageButton
    private lateinit var videoFullscreenSeekFeedbackText: TextView
    private val playbackHandler = Handler(Looper.getMainLooper())
    private val inlineControlsHandler = Handler(Looper.getMainLooper())
    private lateinit var fullscreenOverlayController: FullscreenOverlayController
    private lateinit var fullscreenGestureController: FullscreenGestureController
    private val audioPlaybackController: AudioPlaybackController by lazy {
        AudioPlaybackController(
            context = this,
            onPrepared = {
                updatePlaybackButtons()
                updateNowPlayingInfo()
                updatePlaybackProgress()
                scheduleInlineFullscreenAutoHide()
            },
            onCompleted = ::handlePlaybackCompleted,
            onError = {
                handlePlaybackStartFailure(
                    index = currentAudioStartIndex,
                    skipBudget = currentAudioSkipBudget
                )
            }
        )
    }
    private val media3VideoPlaybackController: Media3VideoPlaybackController by lazy {
        Media3VideoPlaybackController(
            context = this,
            onPrepared = {
                if (playerCategory == PlayerCategory.VIDEO && activeVideoMode != ActiveVideoMode.NONE) {
                    playerArtworkPlaceholder.visibility = View.GONE
                    updatePlaybackButtons()
                    updateNowPlayingInfo()
                    updatePlaybackProgress()
                    if (activeVideoMode == ActiveVideoMode.FULLSCREEN) {
                        fullscreenControlsController.scheduleAutoHide()
                    } else {
                        scheduleInlineFullscreenAutoHide()
                    }
                }
            },
            onCompleted = ::handlePlaybackCompleted,
            onError = ::showPlaybackErrorAndMaybeSkip
        )
    }
    private val fullscreenChromeController: FullscreenChromeController by lazy {
        FullscreenChromeController(
            activity = this,
            appHeader = appHeader,
            mainTabBar = mainTabBar,
            isSettingsVisible = { settingsScreenController.isVisible() }
        )
    }
    private val fullscreenSeekController = FullscreenSeekController()
    private var playerCategory = PlayerCategory.MUSIC
    private var playerItems: List<DownloadEntity> = emptyList()
    private var currentPlayerIndex = -1
    private var currentAudioStartIndex = -1
    private var currentAudioSkipBudget = 0
    private var activeVideoMode = ActiveVideoMode.NONE
    private var userSeeking = false
    private var fullscreenUserSeeking = false
    private var inlineFullscreenVisible = false
    private var hasActiveDownloads = false
    private var currentDownloads: List<DownloadEntity> = emptyList()
    private var backInvokedCallback: OnBackInvokedCallback? = null
    private val settingsPreferences: SharedPreferences
        get() = getSharedPreferences(SETTINGS_PREFS_NAME, MODE_PRIVATE)
    private val defaultQualityPreferences: DefaultQualityPreferences
        get() = DefaultQualityPreferences(settingsPreferences)
    private val settingsPreferencesStore: SettingsPreferencesStore
        get() = SettingsPreferencesStore(settingsPreferences)
    private val fileActionsController: FileActionsController
        get() = FileActionsController(this, ::showToast)
    private val downloadOpenRouter: DownloadOpenRouter
        get() = DownloadOpenRouter(
            getDownloads = { currentDownloads },
            setPlayerCategoryForOpen = ::setPlayerCategoryForOpen,
            showPlayer = { mainNavigationController.showPlayer() },
            startPlaybackAt = { index -> startPlaybackAt(index) },
            openExternal = { download -> fileActionsController.open(download) },
            formatLabelProvider = downloadTextProvider::formatLabel
        )
    private val playerListController: PlayerListController
        get() = PlayerListController()
    private val downloadDetailsDialogController: DownloadDetailsDialogController
        get() = DownloadDetailsDialogController(
            activity = this,
            statusLabelProvider = downloadTextProvider::statusLabel,
            formatLabelProvider = downloadTextProvider::formatLabel,
            progressLabelProvider = downloadTextProvider::progressLabelForDetails,
            onCopyUrl = ::copyDownloadUrl,
            onOpen = ::openDownload,
            onShare = { download -> fileActionsController.share(download) }
        )
    private val qualityDialogController: QualityDialogController
        get() = QualityDialogController(this)
    private val quickDownloadSheetController: QuickDownloadSheetController
        get() = QuickDownloadSheetController(this)
    private val homeDownloadRequestController: HomeDownloadRequestController
        get() = HomeDownloadRequestController(
            selectedDefaultQualityProvider = ::selectedDefaultQualityOption,
            showInvalidUrl = homeScreenController::showError,
            invalidUrlMessageProvider = { getString(R.string.invalid_url) },
            addRecentDownloadUrl = homeScreenController::addRecentDownloadUrl,
            openQualityPicker = ::openYtDlpQualityPicker,
            startDownload = ::startQueuedDownload
        )
    private val mainHeaderController: MainHeaderController
        get() = MainHeaderController(
            currentScreenProvider = { mainNavigationController.currentScreen },
            focusHomeUrlInput = homeScreenController::focusUrlInput,
            showKeyboardForCurrentFocus = ::showKeyboardForCurrentFocus,
            toggleDownloadsSearch = downloadsController::toggleSearch
        )
    private val diagnosticsController: DiagnosticsController
        get() = DiagnosticsController(this, ::showToast)
    private val app: AndroidDownloadApp
        get() = application as AndroidDownloadApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestNotificationPermission()

        val app = application as AndroidDownloadApp
        downloadTextProvider = DownloadTextProvider(this)
        homeScreenController = HomeScreenController(
            activity = this,
            urlInput = findViewById(R.id.urlInput),
            downloadButton = findViewById(R.id.downloadButton),
            errorText = findViewById(R.id.urlErrorText),
            recentUrlsSection = findViewById(R.id.recentDownloadsSection),
            recentUrlsList = findViewById(R.id.recentDownloadsList),
            clearRecentUrlsButton = findViewById(R.id.clearRecentButton),
            recentDownloadsSection = findViewById(R.id.homeRecentDownloadsSection),
            recentDownloadsList = findViewById(R.id.homeRecentDownloadsList),
            recentDownloadsStore = RecentDownloadsStore(settingsPreferences),
            formatLabelProvider = downloadTextProvider::formatLabel,
            statusLabelProvider = { download -> downloadTextProvider.statusLabel(download.status) },
            sizeTextProvider = downloadTextProvider::summarySizeText,
            badgeLabelProvider = downloadTextProvider::typeBadgeLabel,
            onRecentDownloadSelected = ::showDownloadDetailsDialog,
            onClipboardAccepted = { url ->
                mainNavigationController.showHome()
                homeScreenController.setUrl(url)
            }
        )

        appHeader = findViewById(R.id.appHeader)
        mainTabBar = findViewById(R.id.mainTabBar)
        homeContainer = findViewById(R.id.homeContainer)
        playerContainer = findViewById(R.id.playerContainer)
        downloadsContainer = findViewById(R.id.downloadsContainer)
        settingsMenuButton = findViewById(R.id.settingsMenuButton)
        headerSearchButton = findViewById(R.id.headerSearchButton)
        clearFinishedButton = findViewById(R.id.clearFinishedButton)
        homeTabButton = findViewById(R.id.homeTabButton)
        downloadsTabButton = findViewById(R.id.downloadsTabButton)
        playerTabButton = findViewById(R.id.playerTabButton)
        mainNavigationController = MainNavigationController(
            appHeader = appHeader,
            mainTabBar = mainTabBar,
            homeContainer = homeContainer,
            downloadsContainer = downloadsContainer,
            playerContainer = playerContainer,
            settingsMenuButton = settingsMenuButton,
            homeTabButton = homeTabButton,
            downloadsTabButton = downloadsTabButton,
            playerTabButton = playerTabButton,
            hideDownloadsSearch = { clearQuery ->
                downloadsController.hideSearch(clearQuery)
            },
            resetDownloadsFilter = {
                downloadsController.setFilter(DownloadsFilter.ALL, refreshOnly = true)
            },
            hideSettings = {
                settingsScreenController.hide()
            },
            refreshHome = {
                renderHomeRecentDownloads()
                homeScreenController.renderRecentUrls()
            },
            refreshSettings = { scrollToDownloadLocation ->
                updateDefaultQualityText()
                downloadLocationController.updateDownloadLocationText()
                ytDlpUpdateController.updateUiState()
                ytDlpUpdateController.updateAutoUpdateUiState()
                settingsScreenController.show(scrollToDownloadLocation)
            },
            renderPlayerList = {
                renderPlayerList()
            }
        )
        defaultQualityButton = findViewById(R.id.defaultQualityButton)
        playerMusicChip = findViewById(R.id.playerMusicChip)
        playerVideoChip = findViewById(R.id.playerVideoChip)
        playerVideoFrame = findViewById(R.id.playerVideoFrame)
        playerArtworkPlaceholder = findViewById(R.id.playerArtworkPlaceholder)
        media3PlayerVideoView = findViewById(R.id.media3PlayerVideoView)
        playerNowPlayingTitle = findViewById(R.id.playerNowPlayingTitle)
        playerNowPlayingSubtitle = findViewById(R.id.playerNowPlayingSubtitle)
        playerNowPlayingMetaText = findViewById(R.id.playerNowPlayingMetaText)
        playerSeekBar = findViewById(R.id.playerSeekBar)
        playerCurrentTimeText = findViewById(R.id.playerCurrentTimeText)
        playerDurationText = findViewById(R.id.playerDurationText)
        playerPreviousButton = findViewById(R.id.playerPreviousButton)
        playerPlayPauseButton = findViewById(R.id.playerPlayPauseButton)
        playerNextButton = findViewById(R.id.playerNextButton)
        playerFullscreenButton = findViewById(R.id.playerFullscreenButton)
        playerListRenderer = PlayerListRenderer(
            context = this,
            playerList = findViewById(R.id.playerList),
            playerEmptyText = findViewById(R.id.playerEmptyText),
            formatLabelProvider = downloadTextProvider::formatLabel,
            onItemClick = { index -> startPlaybackAt(index) }
        )
        videoFullscreenOverlay = findViewById(R.id.videoFullscreenOverlay)
        videoFullscreenControls = findViewById(R.id.videoFullscreenControls)
        videoFullscreenCloseButton = findViewById(R.id.videoFullscreenCloseButton)
        fullscreenOverlayController = FullscreenOverlayController(
            overlay = videoFullscreenOverlay,
            titleText = findViewById(R.id.videoFullscreenTitleText)
        )
        media3VideoFullscreenView = findViewById(R.id.media3VideoFullscreenView)
        videoFullscreenSeekBar = findViewById(R.id.videoFullscreenSeekBar)
        videoFullscreenCurrentTimeText = findViewById(R.id.videoFullscreenCurrentTimeText)
        videoFullscreenDurationText = findViewById(R.id.videoFullscreenDurationText)
        videoFullscreenPlayPauseButton = findViewById(R.id.videoFullscreenPlayPauseButton)
        playerControlsController = PlayerControlsController(
            playerMusicChip = playerMusicChip,
            playerVideoChip = playerVideoChip,
            inlineVideoView = media3PlayerVideoView,
            playerFullscreenButton = playerFullscreenButton,
            playerNowPlayingTitle = playerNowPlayingTitle,
            playerNowPlayingSubtitle = playerNowPlayingSubtitle,
            playerNowPlayingMetaText = playerNowPlayingMetaText,
            playerSeekBar = playerSeekBar,
            playerCurrentTimeText = playerCurrentTimeText,
            playerDurationText = playerDurationText,
            playerPreviousButton = playerPreviousButton,
            playerPlayPauseButton = playerPlayPauseButton,
            playerNextButton = playerNextButton,
            videoFullscreenSeekBar = videoFullscreenSeekBar,
            videoFullscreenCurrentTimeText = videoFullscreenCurrentTimeText,
            videoFullscreenDurationText = videoFullscreenDurationText,
            videoFullscreenPlayPauseButton = videoFullscreenPlayPauseButton
        )
        videoFullscreenSeekFeedbackText = findViewById(R.id.videoFullscreenSeekFeedbackText)
        fullscreenControlsController = FullscreenControlsController(
            controls = videoFullscreenControls,
            closeButton = videoFullscreenCloseButton,
            playPauseButton = videoFullscreenPlayPauseButton,
            seekFeedbackText = videoFullscreenSeekFeedbackText,
            shouldAutoHide = { isVideoFullscreenOpen() && isCurrentPlaybackRunning() },
            onPlayPauseClick = ::togglePlayback,
            onCloseClick = { closeVideoFullscreen(restoreInline = true) }
        )
        setupPlayer()
        setupSystemBackHandler()

        downloadsController = DownloadsController(
            context = this,
            downloadsList = findViewById(R.id.downloadsList),
            emptyDownloadsText = findViewById(R.id.emptyDownloadsText),
            searchInput = findViewById(R.id.downloadsSearchInput),
            filterAllButton = findViewById(R.id.downloadsFilterAllButton),
            filterActiveButton = findViewById(R.id.downloadsFilterActiveButton),
            filterPausedButton = findViewById(R.id.downloadsFilterPausedButton),
            filterCompletedButton = findViewById(R.id.downloadsFilterCompletedButton),
            filterFailedButton = findViewById(R.id.downloadsFilterFailedButton),
            originFilterButton = findViewById(R.id.downloadsOriginFilterButton),
            activeDownloadCard = findViewById(R.id.activeDownloadCard),
            activeDownloadTitleText = findViewById(R.id.activeDownloadTitleText),
            activeDownloadNameText = findViewById(R.id.activeDownloadNameText),
            activeDownloadFormatText = findViewById(R.id.activeDownloadFormatText),
            activeDownloadProgressText = findViewById(R.id.activeDownloadProgressText),
            activeDownloadProgressBar = findViewById(R.id.activeDownloadProgressBar),
            activeDownloadSpeedText = findViewById(R.id.activeDownloadSpeedText),
            activeDownloadSizeText = findViewById(R.id.activeDownloadSizeText),
            activeDownloadActionsRow = findViewById(R.id.activeDownloadActionsRow),
            activeDownloadPrimaryActionButton = findViewById(R.id.activeDownloadPrimaryActionButton),
            activeDownloadSecondaryActionButton = findViewById(R.id.activeDownloadSecondaryActionButton),
            callbacks = DownloadsController.Callbacks(
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
                    openDownload(download)
                },
                onShareClick = { download ->
                    fileActionsController.share(download)
                },
                onRequestShowKeyboard = ::showKeyboardForCurrentFocus,
                onRequestHideKeyboard = ::hideKeyboard
            )
        )
        clearFinishedDownloadsController = ClearFinishedDownloadsController(
            activity = this,
            scope = scope,
            clearFinishedDownloadsAction = {
                withContext(Dispatchers.IO) {
                    app.container.repository.removeFinalizedDownloads().size
                }
            },
            showToast = ::showToast
        )

        settingsScreenController = SettingsScreenController(
            activity = this,
            diagnosticsController = diagnosticsController,
            defaultQualityPreferences = defaultQualityPreferences,
            qualityDialogController = qualityDialogController,
            defaultQualityValueText = findViewById(R.id.defaultQualityValueText),
            settingsContainer = findViewById(R.id.settingsContainer),
            downloadLocationCard = findViewById(R.id.downloadLocationCard),
            downloadLocationText = findViewById(R.id.downloadLocationText),
            chooseDownloadLocationButton = findViewById(R.id.chooseDownloadLocationButton),
            useDefaultDownloadLocationButton = findViewById(R.id.useDefaultDownloadLocationButton),
            ytdlpUpdateStatusText = findViewById(R.id.ytdlpUpdateStatusText),
            updateYtDlpButton = findViewById(R.id.updateYtDlpButton),
            autoUpdateYtDlpButton = findViewById(R.id.autoUpdateYtDlpButton),
            diagnosticsButton = findViewById(R.id.diagnosticsButton),
            aboutAppButton = findViewById(R.id.aboutAppButton),
            settingsCloseButton = findViewById(R.id.settingsCloseButton),
            onChooseDownloadLocation = { downloadLocationController.chooseDownloadLocation() },
            onUseDefaultDownloadLocation = { downloadLocationController.useDefaultDownloadLocation() },
            onUpdateYtDlp = { ytDlpUpdateController.updateYtDlpManually() },
            onToggleAutoUpdateYtDlp = { ytDlpUpdateController.toggleAutoUpdateYtDlp() },
            onCloseSettings = mainNavigationController::closeSettingsOverlay
        )
        settingsController = settingsScreenController.settingsController
        downloadLocationController = DownloadLocationController(
            activity = this,
            settingsController = settingsController,
            requestCode = REQUEST_DOWNLOAD_TREE,
            showToast = ::showToast
        )
        ytDlpUpdateController = YtDlpUpdateController(
            context = this,
            settingsController = settingsController,
            settingsPreferencesStore = settingsPreferencesStore,
            scope = scope,
            updateManually = {
                withContext(Dispatchers.IO) {
                    app.container.ytDlpDownloader.updateManually()
                }
            },
            showToast = ::showToast
        )

        headerSearchButton.setOnClickListener { handleHeaderSearchClick() }
        defaultQualityButton.setOnClickListener { showDefaultQualityDialog() }
        clearFinishedButton.setOnClickListener {
            clearFinishedDownloadsController.showClearFinishedDownloadsDialog()
        }
        homeScreenController.onDownloadRequested = onDownloadClick@{ rawUrl ->
            homeDownloadRequestController.handleDownloadRequest(
                rawUrl = rawUrl,
                homeController = homeScreenController.homeController
            )
        }

        scope.launch {
            app.container.repository.observeDownloads().collectLatest { downloads ->
                currentDownloads = downloads
                downloadsController.submitDownloads(downloads)
                renderHomeRecentDownloads()
                renderPlayerList()
                val newHasActiveDownloads = downloads.any {
                    it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.PREPARING
                }
                if (hasActiveDownloads != newHasActiveDownloads) {
                    hasActiveDownloads = newHasActiveDownloads
                    ytDlpUpdateController.setHasActiveDownloads(newHasActiveDownloads)
                }
            }
        }

        updateDefaultQualityText()
        downloadLocationController.updateDownloadLocationText()
        renderHomeRecentDownloads()
        homeScreenController.renderRecentUrls()
        mainNavigationController.showHome()
        handleIntent(intent)
        homeScreenController.maybeShowClipboardPrompt(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
        homeScreenController.maybeShowClipboardPrompt(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (downloadLocationController.handleActivityResult(requestCode, resultCode, data)) return
    }

    override fun onDestroy() {
        unregisterSystemBackHandler()
        releasePlayer()
        scope.cancel()
        super.onDestroy()
    }

    override fun onPause() {
        pauseCurrentPlayback()
        super.onPause()
    }

    override fun onStop() {
        pauseCurrentPlayback()
        super.onStop()
    }

    private fun setupPlayer() {
        playerMusicChip.setOnClickListener { setPlayerCategory(PlayerCategory.MUSIC) }
        playerVideoChip.setOnClickListener { setPlayerCategory(PlayerCategory.VIDEO) }
        playerPlayPauseButton.setOnClickListener { togglePlayback() }
        playerPreviousButton.setOnClickListener { playAdjacent(offset = -1) }
        playerNextButton.setOnClickListener { playAdjacent(offset = 1) }
        playerFullscreenButton.setOnClickListener { openCurrentVideoFullscreen() }
        playerVideoFrame.setOnClickListener { toggleInlineFullscreenButton() }
        fullscreenGestureController = FullscreenGestureController(
            context = this,
            touchTarget = videoFullscreenOverlay,
            onSingleTap = {
                fullscreenControlsController.toggleControls()
            },
            onDoubleTap = { tapX, width ->
                seekFullscreenBy(
                    deltaMs = fullscreenSeekController.deltaForTap(
                        tapX = tapX,
                        width = width
                    )
                )
                fullscreenControlsController.showControls()
                fullscreenControlsController.scheduleAutoHide()
            }
        )
        fullscreenGestureController.attach()
        playerSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val duration = currentDuration()
                if (duration > 0) {
                    playerControlsController.updateInlineSeekPreview(
                        PlayerProgressCalculator.formatPlaybackTime(
                            PlayerProgressCalculator.progressToPosition(
                                progress = progress,
                                durationMs = duration,
                                maxProgress = playerSeekBar.max
                            )
                        )
                    )
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                userSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val duration = currentDuration()
                if (duration > 0 && isCurrentPlaybackPrepared()) {
                    seekCurrentPlayback(
                        PlayerProgressCalculator.progressToPosition(
                            progress = seekBar?.progress ?: 0,
                            durationMs = duration,
                            maxProgress = playerSeekBar.max
                        )
                    )
                }
                userSeeking = false
                updatePlaybackProgress()
            }
        })
        videoFullscreenSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val duration = currentDuration()
                if (duration > 0) {
                    playerControlsController.updateFullscreenSeekPreview(
                        PlayerProgressCalculator.formatPlaybackTime(
                            PlayerProgressCalculator.progressToPosition(
                                progress = progress,
                                durationMs = duration,
                                maxProgress = playerSeekBar.max
                            )
                        )
                    )
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                fullscreenUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val duration = currentDuration()
                if (duration > 0 && isCurrentPlaybackPrepared()) {
                    seekCurrentPlayback(
                        PlayerProgressCalculator.progressToPosition(
                            progress = seekBar?.progress ?: 0,
                            durationMs = duration,
                            maxProgress = playerSeekBar.max
                        )
                    )
                }
                fullscreenUserSeeking = false
                updatePlaybackProgress()
            }
        })
        updateNowPlayingInfo()
        renderPlayerList()
    }

    private fun setPlayerCategory(category: PlayerCategory) {
        if (playerCategory == category) return
        pauseCurrentPlayback()
        stopCurrentPlayback(clearSelection = true)
        playerCategory = category
        updatePlayerCategoryUi()
        renderPlayerList()
    }

    private fun updatePlayerCategoryUi() {
        val isVideoVisible = playerCategory == PlayerCategory.VIDEO &&
            currentPlayerIndex >= 0 &&
            activeVideoMode == ActiveVideoMode.INLINE &&
            !isVideoFullscreenOpen()
        playerControlsController.updateCategoryUi(
            category = playerCategory,
            isVideoVisible = isVideoVisible,
            showFullscreenButton = shouldShowInlineFullscreenButton()
        )
    }

    private fun renderPlayerList() {
        val previousPlayingId = currentPlayingDownload()?.id
        val state = playerListController.buildState(
            downloads = currentDownloads,
            category = playerCategory,
            currentIndex = currentPlayerIndex,
            currentPlayingId = previousPlayingId,
            matchesPlayerCategory = { download, category ->
                DownloadOpenRouter.matchesPlayerCategory(download, category, downloadTextProvider::formatLabel)
            }
        )
        playerItems = state.items
        currentPlayerIndex = state.currentIndex
        if (state.shouldClearSelection) {
            stopCurrentPlayback(clearSelection = true)
        }

        updatePlayerCategoryUi()
        playerListRenderer.render(
            items = playerItems,
            category = playerCategory,
            currentIndex = currentPlayerIndex
        )
        updatePlaybackButtons()
    }

    private fun startPlaybackAt(index: Int, skipBudget: Int = playerItems.size) {
        val download = playerItems.getOrNull(index) ?: return
        val playbackUri = resolvePlaybackUri(download)
        if (playbackUri == null) {
            handlePlaybackStartFailure(index, skipBudget)
            return
        }

        stopCurrentPlayback(clearSelection = false)
        currentPlayerIndex = index
        updateNowPlayingInfo(download)
        playerControlsController.resetInlineProgress(getString(R.string.player_time_zero))
        updatePlayerCategoryUi()

        if (playerCategory == PlayerCategory.MUSIC) {
            startAudioPlayback(playbackUri, index, skipBudget)
        } else {
            startVideoPlayback(playbackUri, index, skipBudget)
        }
        renderPlayerList()
    }

    private fun startAudioPlayback(uri: Uri, index: Int, skipBudget: Int) {
        stopVideoPlayback()
        stopFullscreenVideoPlayback()
        activeVideoMode = ActiveVideoMode.NONE
        playerArtworkPlaceholder.visibility = View.VISIBLE
        media3PlayerVideoView.visibility = View.GONE
        currentAudioStartIndex = index
        currentAudioSkipBudget = skipBudget
        audioPlaybackController.start(uri)
    }

    private fun startVideoPlayback(uri: Uri, index: Int, skipBudget: Int) {
        stopAudioPlayback()
        closeVideoFullscreen(restoreInline = false)
        playerArtworkPlaceholder.visibility = View.GONE
        media3PlayerVideoView.visibility = View.VISIBLE
        activeVideoMode = ActiveVideoMode.INLINE
        prepareInlineVideo(uri = uri, positionMs = 0, playWhenReady = true, onError = {
            handlePlaybackStartFailure(index, skipBudget)
        })
    }

    private fun prepareInlineVideo(
        uri: Uri,
        positionMs: Int,
        playWhenReady: Boolean,
        onError: (() -> Unit)? = null
    ) {
        stopFullscreenVideoPlayback()
        activeVideoMode = ActiveVideoMode.INLINE
        media3VideoFullscreenView.visibility = View.GONE
        media3VideoPlaybackController.detach(media3VideoFullscreenView)
        media3PlayerVideoView.visibility = View.VISIBLE
        media3VideoPlaybackController.attach(media3PlayerVideoView)
        media3VideoPlaybackController.start(
            uri = uri,
            positionMs = positionMs,
            playWhenReady = playWhenReady,
            onStartError = onError
        )
    }

    private fun toggleInlineFullscreenButton() {
        if (playerCategory != PlayerCategory.VIDEO || currentPlayerIndex < 0) return
        inlineFullscreenVisible = !inlineFullscreenVisible
        updatePlayerCategoryUi()
        if (inlineFullscreenVisible) {
            scheduleInlineFullscreenAutoHide()
        } else {
            inlineControlsHandler.removeCallbacksAndMessages(null)
        }
    }

    private fun scheduleInlineFullscreenAutoHide() {
        inlineControlsHandler.removeCallbacksAndMessages(null)
        if (!inlineFullscreenVisible || !isCurrentPlaybackRunning()) return
        inlineControlsHandler.postDelayed({
            inlineFullscreenVisible = false
            updatePlayerCategoryUi()
        }, 3_000L)
    }

    private fun shouldShowInlineFullscreenButton(): Boolean {
        return inlineFullscreenVisible &&
            playerCategory == PlayerCategory.VIDEO &&
            currentPlayerIndex >= 0 &&
            media3PlayerVideoView.visibility == View.VISIBLE &&
            !isVideoFullscreenOpen()
    }

    private fun handlePlaybackStartFailure(index: Int, skipBudget: Int) {
        showToast(getString(R.string.player_playback_error))
        when (
            val action = PlayerPlaybackFailureResolver.resolveStartFailure(
                itemCount = playerItems.size,
                failedIndex = index,
                skipBudget = skipBudget
            )
        ) {
            is PlayerPlaybackFailureAction.SkipToNext -> startPlaybackAt(action.index, skipBudget - 1)
            PlayerPlaybackFailureAction.Stop -> stopCurrentPlayback(clearSelection = true)
        }
    }

    private fun showPlaybackErrorAndMaybeSkip() {
        val index = currentPlayerIndex
        showToast(getString(R.string.player_playback_error))
        when (
            val action = PlayerPlaybackFailureResolver.resolvePlaybackError(
                itemCount = playerItems.size,
                currentIndex = index
            )
        ) {
            is PlayerPlaybackFailureAction.SkipToNext -> startPlaybackAt(action.index)
            PlayerPlaybackFailureAction.Stop -> stopCurrentPlayback(clearSelection = true)
        }
    }

    private fun togglePlayback() {
        if (currentPlayerIndex < 0) {
            if (playerItems.isNotEmpty()) startPlaybackAt(0)
            return
        }
        if (isCurrentPlaybackRunning()) {
            pauseCurrentPlayback()
        } else {
            resumeCurrentPlayback()
        }
    }

    private fun pauseCurrentPlayback() {
        audioPlaybackController.pause()
        media3VideoPlaybackController.pause()
        playbackHandler.removeCallbacksAndMessages(null)
        inlineControlsHandler.removeCallbacksAndMessages(null)
        if (isVideoFullscreenOpen()) {
            fullscreenControlsController.showControls()
            fullscreenControlsController.clearCallbacks()
        }
        updatePlaybackButtons()
        updateNowPlayingInfo()
    }

    private fun resumeCurrentPlayback() {
        if (playerCategory == PlayerCategory.MUSIC) {
            audioPlaybackController.resume()
        } else if (activeVideoMode != ActiveVideoMode.NONE) {
            media3VideoPlaybackController.resume()
        }
        updatePlaybackButtons()
        updateNowPlayingInfo()
        updatePlaybackProgress()
        fullscreenControlsController.scheduleAutoHide()
        scheduleInlineFullscreenAutoHide()
    }

    private fun stopCurrentPlayback(clearSelection: Boolean) {
        playbackHandler.removeCallbacksAndMessages(null)
        inlineControlsHandler.removeCallbacksAndMessages(null)
        fullscreenControlsController.clearCallbacks()
        userSeeking = false
        fullscreenUserSeeking = false
        closeVideoFullscreen(restoreInline = false)
        stopAudioPlayback()
        stopVideoPlayback()
        if (clearSelection) {
            currentPlayerIndex = -1
            playerControlsController.updateNowPlaying(
                title = getString(R.string.player_nothing_selected),
                subtitle = getString(R.string.player_select_file),
                meta = getString(R.string.player_status_stopped)
            )
            playerControlsController.resetInlineProgress(getString(R.string.player_time_zero))
            playerArtworkPlaceholder.visibility = View.VISIBLE
            media3PlayerVideoView.visibility = View.GONE
            inlineFullscreenVisible = false
            updatePlayerCategoryUi()
        }
        updateNowPlayingInfo()
        updatePlaybackButtons()
    }

    private fun stopAudioPlayback() {
        audioPlaybackController.stop()
        currentAudioStartIndex = -1
        currentAudioSkipBudget = 0
    }

    private fun stopVideoPlayback() {
        media3VideoPlaybackController.stop()
        media3VideoPlaybackController.detach(media3PlayerVideoView)
        media3VideoPlaybackController.detach(media3VideoFullscreenView)
        media3PlayerVideoView.visibility = View.GONE
        media3VideoFullscreenView.visibility = View.GONE
        if (activeVideoMode == ActiveVideoMode.INLINE || activeVideoMode == ActiveVideoMode.FULLSCREEN) {
            activeVideoMode = ActiveVideoMode.NONE
        }
    }

    private fun stopFullscreenVideoPlayback() {
        media3VideoPlaybackController.detach(media3VideoFullscreenView)
        media3VideoFullscreenView.visibility = View.GONE
        fullscreenUserSeeking = false
        if (activeVideoMode == ActiveVideoMode.FULLSCREEN) {
            activeVideoMode = ActiveVideoMode.NONE
        }
    }

    private fun releasePlayer() {
        inlineControlsHandler.removeCallbacksAndMessages(null)
        fullscreenControlsController.clearCallbacks()
        stopCurrentPlayback(clearSelection = true)
        media3VideoPlaybackController.release()
    }

    private fun playAdjacent(offset: Int) {
        val target = PlayerAdjacentNavigator.resolveTarget(
            itemCount = playerItems.size,
            currentIndex = currentPlayerIndex,
            offset = offset
        )
        if (target.shouldStart && target.targetIndex != null) {
            startPlaybackAt(target.targetIndex)
        }
    }

    private fun handlePlaybackCompleted() {
        when (
            val action = PlayerCompletionResolver.resolveCompletion(
                itemCount = playerItems.size,
                currentIndex = currentPlayerIndex
            )
        ) {
            is PlayerCompletionAction.PlayNext -> startPlaybackAt(action.index)
            PlayerCompletionAction.StopAtEnd -> {
                stopCurrentPlayback(clearSelection = false)
                updatePlaybackButtons()
                updateNowPlayingInfo()
            }
        }
    }

    private fun updatePlaybackProgress() {
        playbackHandler.removeCallbacksAndMessages(null)
        if (currentPlayerIndex < 0 || !isCurrentPlaybackPrepared()) {
            updatePlaybackButtons()
            updateNowPlayingInfo()
            return
        }
        val duration = currentDuration()
        val position = currentPosition()
        if (duration > 0) {
            playerControlsController.updateProgress(
                progress = PlayerProgressCalculator.positionToProgress(
                    positionMs = position,
                    durationMs = duration,
                    maxProgress = playerSeekBar.max
                ),
                currentTime = PlayerProgressCalculator.formatPlaybackTime(position),
                duration = PlayerProgressCalculator.formatPlaybackTime(duration),
                updateInlineSeek = !userSeeking,
                updateFullscreenSeek = !fullscreenUserSeeking
            )
        }
        updatePlaybackButtons()
        updateNowPlayingInfo()
        if (isCurrentPlaybackRunning()) {
            playbackHandler.postDelayed({ updatePlaybackProgress() }, 500L)
        }
    }

    private fun updatePlaybackButtons() {
        val hasItems = playerItems.isNotEmpty()
        playerControlsController.updatePlaybackButtons(
            hasItems = hasItems,
            currentIndex = currentPlayerIndex,
            lastIndex = playerItems.lastIndex,
            isRunning = isCurrentPlaybackRunning(),
            playText = getString(R.string.player_play),
            pauseText = getString(R.string.player_pause)
        )
    }

    private fun updateNowPlayingInfo(download: DownloadEntity? = currentPlayingDownload()) {
        val current = download
        if (current == null || currentPlayerIndex < 0) {
            val text = PlayerNowPlayingTextFormatter.buildEmptyText(
                title = getString(R.string.player_nothing_selected),
                subtitle = getString(R.string.player_select_file),
                meta = getString(R.string.player_status_stopped)
            )
            playerControlsController.updateNowPlaying(
                title = text.title,
                subtitle = text.subtitle,
                meta = text.meta
            )
            return
        }

        val formatLabel = downloadTextProvider.formatLabel(current)
        val text = PlayerNowPlayingTextFormatter.buildSelectedText(
            fileName = current.fileName,
            typeLabel = playerTypeLabel(current),
            formatLabel = formatLabel,
            statusLabel = playbackStatusLabel(),
            currentTime = playerCurrentTimeText.text,
            duration = playerDurationText.text
        )
        playerControlsController.updateNowPlaying(
            title = text.title,
            subtitle = text.subtitle,
            meta = text.meta
        )
    }

    private fun playbackStatusLabel(): String {
        return when {
            currentPlayerIndex < 0 -> getString(R.string.player_status_stopped)
            isCurrentPlaybackRunning() -> getString(R.string.player_status_playing)
            isCurrentPlaybackPrepared() -> getString(R.string.player_status_paused)
            else -> getString(R.string.player_status_stopped)
        }
    }

    private fun playerTypeLabel(download: DownloadEntity): String {
        return PlayerMediaLabelResolver.typeLabel(
            download = download,
            formatLabel = downloadTextProvider.formatLabel(download)
        )
    }

    private fun currentPlayingDownload(): DownloadEntity? {
        return playerItems.getOrNull(currentPlayerIndex)
    }

    private fun activePlaybackSource(): ActivePlaybackSource {
        return when {
            playerCategory == PlayerCategory.MUSIC -> ActivePlaybackSource.AUDIO
            activeVideoMode == ActiveVideoMode.FULLSCREEN -> ActivePlaybackSource.FULLSCREEN_VIDEO
            activeVideoMode == ActiveVideoMode.INLINE -> ActivePlaybackSource.INLINE_VIDEO
            else -> ActivePlaybackSource.NONE
        }
    }

    private fun isCurrentPlaybackRunning(): Boolean {
        return when (activePlaybackSource()) {
            ActivePlaybackSource.AUDIO -> audioPlaybackController.isPlaying()
            ActivePlaybackSource.FULLSCREEN_VIDEO,
            ActivePlaybackSource.INLINE_VIDEO -> media3VideoPlaybackController.isPlaying()
            ActivePlaybackSource.NONE -> false
        }
    }

    private fun isCurrentPlaybackPrepared(): Boolean {
        return when (activePlaybackSource()) {
            ActivePlaybackSource.AUDIO -> audioPlaybackController.isPrepared()
            ActivePlaybackSource.FULLSCREEN_VIDEO,
            ActivePlaybackSource.INLINE_VIDEO -> media3VideoPlaybackController.isPrepared()
            ActivePlaybackSource.NONE -> false
        }
    }

    private fun currentDuration(): Int {
        return runCatching {
            when (activePlaybackSource()) {
                ActivePlaybackSource.AUDIO ->
                    audioPlaybackController.duration()
                ActivePlaybackSource.FULLSCREEN_VIDEO,
                ActivePlaybackSource.INLINE_VIDEO ->
                    media3VideoPlaybackController.duration()
                ActivePlaybackSource.NONE -> 0
            }
        }.getOrDefault(0)
    }

    private fun currentPosition(): Int {
        return runCatching {
            when (activePlaybackSource()) {
                ActivePlaybackSource.AUDIO ->
                    audioPlaybackController.currentPosition()
                ActivePlaybackSource.FULLSCREEN_VIDEO,
                ActivePlaybackSource.INLINE_VIDEO ->
                    media3VideoPlaybackController.currentPosition()
                ActivePlaybackSource.NONE -> 0
            }
        }.getOrDefault(0)
    }

    private fun seekCurrentPlayback(positionMs: Int) {
        runCatching {
            when (activePlaybackSource()) {
                ActivePlaybackSource.AUDIO -> audioPlaybackController.seekTo(positionMs)
                ActivePlaybackSource.FULLSCREEN_VIDEO,
                ActivePlaybackSource.INLINE_VIDEO -> media3VideoPlaybackController.seekTo(positionMs)
                ActivePlaybackSource.NONE -> Unit
            }
        }
    }

    private fun openCurrentVideoFullscreen() {
        if (playerCategory != PlayerCategory.VIDEO) return
        val download = currentPlayingDownload() ?: return

        if (resolvePlaybackUri(download) == null) {
            showToast(getString(R.string.player_playback_error))
            return
        }

        val position = currentPosition()
        val wasPlaying = isCurrentPlaybackRunning()
        playbackHandler.removeCallbacksAndMessages(null)
        inlineControlsHandler.removeCallbacksAndMessages(null)
        inlineFullscreenVisible = false
        media3VideoPlaybackController.detach(media3PlayerVideoView)
        media3PlayerVideoView.visibility = View.GONE
        playerArtworkPlaceholder.visibility = View.VISIBLE
        playerControlsController.updateFullscreenProgress(
            progress = 0,
            currentTime = PlayerProgressCalculator.formatPlaybackTime(position),
            duration = playerDurationText.text
        )
        fullscreenChromeController.enterFullscreen()
        fullscreenOverlayController.show(download.fileName)
        fullscreenControlsController.showControls()
        activeVideoMode = ActiveVideoMode.FULLSCREEN
        media3VideoFullscreenView.visibility = View.VISIBLE
        media3VideoPlaybackController.attach(media3VideoFullscreenView)
        if (wasPlaying) {
            media3VideoPlaybackController.resume()
        } else {
            media3VideoPlaybackController.pause()
        }
        updatePlaybackProgress()
    }

    private fun closeVideoFullscreen(restoreInline: Boolean) {
        if (!isVideoFullscreenOpen()) return
        val download = currentPlayingDownload()
        val wasPlaying = isCurrentPlaybackRunning()
        playbackHandler.removeCallbacksAndMessages(null)
        media3VideoPlaybackController.detach(media3VideoFullscreenView)
        media3VideoFullscreenView.visibility = View.GONE
        fullscreenOverlayController.hide()
        fullscreenControlsController.clearCallbacks()
        fullscreenChromeController.exitFullscreen()
        playerControlsController.resetFullscreenProgress(getString(R.string.player_time_zero))

        if (restoreInline && playerCategory == PlayerCategory.VIDEO && download != null) {
            if (resolvePlaybackUri(download) == null) {
                showToast(getString(R.string.player_playback_error))
                stopCurrentPlayback(clearSelection = true)
                return
            }
            playerArtworkPlaceholder.visibility = View.GONE
            media3PlayerVideoView.visibility = View.VISIBLE
            activeVideoMode = ActiveVideoMode.INLINE
            media3VideoPlaybackController.attach(media3PlayerVideoView)
            if (wasPlaying) {
                media3VideoPlaybackController.resume()
            } else {
                media3VideoPlaybackController.pause()
            }
            updatePlaybackProgress()
        } else {
            activeVideoMode = ActiveVideoMode.NONE
            media3VideoPlaybackController.stop()
            updatePlaybackButtons()
            updateNowPlayingInfo()
        }
    }

    private fun isVideoFullscreenOpen(): Boolean {
        return ::fullscreenOverlayController.isInitialized && fullscreenOverlayController.isOpen()
    }

    private fun seekFullscreenBy(deltaMs: Int) {
        if (!isVideoFullscreenOpen() || !media3VideoPlaybackController.isPrepared()) return
        val duration = currentDuration()
        if (duration <= 0) return
        val target = fullscreenSeekController.targetPosition(
            currentPositionMs = currentPosition(),
            durationMs = duration,
            deltaMs = deltaMs
        )
        seekCurrentPlayback(target)
        updatePlaybackProgress()
        fullscreenControlsController.showSeekFeedback(
            if (deltaMs < 0) getString(R.string.player_rewind_10) else getString(R.string.player_forward_10)
        )
    }

    private fun resolvePlaybackUri(download: DownloadEntity): Uri? {
        return PlaybackUriResolver(contentResolver).resolve(download.destinationUri)
    }

    private fun handleHeaderSearchClick() {
        mainHeaderController.handleSearchClick()
    }

    private fun showKeyboardForCurrentFocus() {
        val inputManager = getSystemService(InputMethodManager::class.java) ?: return
        currentFocus?.let { inputManager.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun hideKeyboard(view: View) {
        val inputManager = getSystemService(InputMethodManager::class.java) ?: return
        inputManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun renderHomeRecentDownloads() {
        homeScreenController.renderRecentDownloads(currentDownloads)
    }

    private fun showDownloadDetailsDialog(download: DownloadEntity) {
        downloadDetailsDialogController.show(download)
    }

    private fun copyDownloadUrl(download: DownloadEntity) {
        val clipboard = getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(
            ClipData.newPlainText(getString(R.string.details_copy_url), download.sourceUrl)
        )
        showToast(getString(R.string.url_copied))
    }

    private fun openDownload(download: DownloadEntity) {
        downloadOpenRouter.open(download)
    }

    private fun setPlayerCategoryForOpen(category: PlayerCategory) {
        if (playerCategory != category) {
            pauseCurrentPlayback()
            stopCurrentPlayback(clearSelection = true)
            playerCategory = category
        }
    }

    private fun setupSystemBackHandler() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val callback = OnBackInvokedCallback {
            if (!handleBackNavigation()) {
                finish()
            }
        }
        onBackInvokedDispatcher.registerOnBackInvokedCallback(
            OnBackInvokedDispatcher.PRIORITY_DEFAULT,
            callback
        )
        backInvokedCallback = callback
    }

    private fun unregisterSystemBackHandler() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val callback = backInvokedCallback ?: return
        onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
        backInvokedCallback = null
    }

    private fun handleBackNavigation(): Boolean {
        if (isVideoFullscreenOpen()) {
            closeVideoFullscreen(restoreInline = true)
            return true
        }
        if (settingsScreenController.isVisible()) {
            mainNavigationController.closeSettingsOverlay()
            return true
        }
        return false
    }

    private fun handleIntent(intent: Intent?) {
        val openDownloadId = intent?.getLongExtra(EXTRA_OPEN_DOWNLOAD_ID, -1L) ?: -1L
        if (openDownloadId > 0L) {
            handleOpenDownloadIntent(openDownloadId)
            return
        }

        if (intent?.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false) == true) {
            mainNavigationController.showDownloads()
            return
        }

        if (intent?.action == Intent.ACTION_SEND &&
            intent.type?.equals("text/plain", ignoreCase = true) == true
        ) {
            handleSharedText(intent)
        }
    }

    private fun handleSharedText(intent: Intent) {
        val sharedUrl = SharedTextUrlExtractor.extract(
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        )
        if (sharedUrl == null) {
            mainNavigationController.showHome()
            showToast(getString(R.string.invalid_url))
            return
        }

        mainNavigationController.showHome()
        homeScreenController.setUrl(sharedUrl)
        showToast(getString(R.string.shared_link_received))
        if (!DownloadSourceClassifier.shouldUseHttpDownloader(sharedUrl)) {
            quickDownloadSheetController.show(
                url = sharedUrl,
                options = downloadQualityOptions()
            ) { option ->
                startQueuedDownload(
                    url = sharedUrl,
                    qualitySelector = option.formatSelector,
                    homeController = homeScreenController.homeController
                )
            }
        }
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
            fileActionsController.open(download)
        }
    }

    private fun openYtDlpQualityPicker(
        url: String,
        homeController: HomeController? = null
    ) {
        val options = downloadQualityOptions()
        qualityDialogController.showDownloadQualityDialog(options) { option ->
            startQueuedDownload(
                url = url,
                qualitySelector = option.formatSelector,
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
            var resetLoadingOnExit = true
            try {
                val downloadId = withContext(Dispatchers.IO) {
                    app.container.queue.enqueue(url, qualitySelector)
                }
                homeController?.clear()
                homeController?.setLoading(false)
                resetLoadingOnExit = false
                mainNavigationController.showDownloads()
                DownloadForegroundService.start(this@MainActivity, downloadId)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                showToast(getString(R.string.download_error_unable_to_download))
            } finally {
                if (resetLoadingOnExit) {
                    homeController?.setLoading(false)
                }
            }
        }
    }

    private fun showDefaultQualityDialog() {
        settingsScreenController.showDefaultQualityDialog()
    }

    private fun updateDefaultQualityText() {
        settingsScreenController.updateDefaultQualityText()
    }

    private fun selectedDefaultQualityOption(): QualityOptionUi {
        return settingsScreenController.selectedDefaultQualityOption()
    }

    private fun downloadQualityOptions(): List<QualityOptionUi> {
        return settingsScreenController.downloadQualityOptions()
    }

    override fun onBackPressed() {
        if (handleBackNavigation()) {
            return
        }
        super.onBackPressed()
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
        const val EXTRA_OPEN_DOWNLOAD_ID = "com.androiddownload.extra.OPEN_DOWNLOAD_ID"
        private const val SETTINGS_PREFS_NAME = "aio_downloader_settings"
        private const val REQUEST_DOWNLOAD_TREE = 2002
    }
}
