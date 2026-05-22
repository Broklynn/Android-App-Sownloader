package com.androiddownload

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.androiddownload.core.files.FileActionsController
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import com.androiddownload.core.preferences.DefaultQualityPreferences
import com.androiddownload.core.preferences.RecentDownloadsStore
import com.androiddownload.core.preferences.SettingsPreferencesStore
import com.androiddownload.core.utils.DownloadDestinationResolver
import com.androiddownload.core.utils.FileSizeFormatter
import com.androiddownload.core.utils.DownloadSourceClassifier
import com.androiddownload.core.utils.SharedTextUrlExtractor
import com.androiddownload.core.utils.YtDlpQualityOptions
import com.androiddownload.core.utils.YtDlpDiagnostics
import com.androiddownload.download.service.DownloadForegroundService
import com.androiddownload.ui.downloads.DownloadDetailsDialogController
import com.androiddownload.ui.downloads.DownloadsController
import com.androiddownload.ui.downloads.DownloadsFilter
import com.androiddownload.ui.downloads.DownloadOpenRouter
import com.androiddownload.ui.common.DarkDialogButton
import com.androiddownload.ui.common.DarkDialogFactory
import com.androiddownload.ui.home.ClipboardLinkPromptController
import com.androiddownload.ui.home.HomeDownloadRequestController
import com.androiddownload.ui.home.HomeController
import com.androiddownload.ui.home.HomeRecentDownloadsRenderer
import com.androiddownload.ui.navigation.MainHeaderController
import com.androiddownload.ui.navigation.MainNavigationController
import com.androiddownload.ui.navigation.PrimaryScreen
import com.androiddownload.ui.downloads.QualityDialogController
import com.androiddownload.ui.downloads.QualityOptionUi
import com.androiddownload.ui.downloads.QuickDownloadSheetController
import com.androiddownload.ui.player.ActiveVideoMode
import com.androiddownload.ui.player.AspectRatioVideoView
import com.androiddownload.ui.player.PlayerCategory
import com.androiddownload.ui.player.PlayerControlsController
import com.androiddownload.ui.player.PlayerListController
import com.androiddownload.ui.player.PlayerListRenderer
import com.androiddownload.ui.player.PlayerProgressCalculator
import com.androiddownload.ui.settings.DiagnosticsController
import com.androiddownload.ui.settings.SettingsController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var appHeader: View
    private lateinit var mainTabBar: View
    private lateinit var homeContainer: View
    private lateinit var playerContainer: View
    private lateinit var downloadsContainer: View
    private lateinit var settingsMenuButton: ImageButton
    private lateinit var headerSearchButton: ImageButton
    private lateinit var clearFinishedButton: Button
    private lateinit var recentDownloadsSection: LinearLayout
    private lateinit var clearRecentButton: Button
    private lateinit var recentDownloadsList: LinearLayout
    private lateinit var homeRecentDownloadsSection: LinearLayout
    private lateinit var homeRecentDownloadsList: LinearLayout
    private lateinit var homeController: HomeController
    private lateinit var clipboardLinkPromptController: ClipboardLinkPromptController
    private lateinit var homeRecentDownloadsRenderer: HomeRecentDownloadsRenderer
    private lateinit var downloadsController: DownloadsController
    private lateinit var settingsController: SettingsController
    private lateinit var mainNavigationController: MainNavigationController
    private lateinit var homeTabButton: Button
    private lateinit var downloadsTabButton: Button
    private lateinit var playerTabButton: Button
    private lateinit var defaultQualityValueText: TextView
    private lateinit var defaultQualityButton: Button
    private lateinit var playerMusicChip: Button
    private lateinit var playerVideoChip: Button
    private lateinit var playerVideoFrame: View
    private lateinit var playerArtworkPlaceholder: TextView
    private lateinit var playerVideoView: AspectRatioVideoView
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
    private lateinit var playerListRenderer: PlayerListRenderer
    private lateinit var videoFullscreenOverlay: View
    private lateinit var videoFullscreenControls: View
    private lateinit var videoFullscreenCloseButton: ImageButton
    private lateinit var videoFullscreenTitleText: TextView
    private lateinit var videoFullscreenView: AspectRatioVideoView
    private lateinit var videoFullscreenSeekBar: SeekBar
    private lateinit var videoFullscreenCurrentTimeText: TextView
    private lateinit var videoFullscreenDurationText: TextView
    private lateinit var videoFullscreenPlayPauseButton: ImageButton
    private lateinit var videoFullscreenSeekFeedbackText: TextView
    private val playbackHandler = Handler(Looper.getMainLooper())
    private val inlineControlsHandler = Handler(Looper.getMainLooper())
    private val fullscreenControlsHandler = Handler(Looper.getMainLooper())
    private val fullscreenFeedbackHandler = Handler(Looper.getMainLooper())
    private var audioPlayer: MediaPlayer? = null
    private var playerCategory = PlayerCategory.MUSIC
    private var playerItems: List<DownloadEntity> = emptyList()
    private var currentPlayerIndex = -1
    private var audioPrepared = false
    private var videoPrepared = false
    private var fullscreenVideoPrepared = false
    private var activeVideoMode = ActiveVideoMode.NONE
    private var userSeeking = false
    private var fullscreenUserSeeking = false
    private var inlineFullscreenVisible = false
    private var fullscreenControlsVisible = true
    private lateinit var fullscreenGestureDetector: GestureDetector
    private var previousRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var previousSystemUiVisibility = 0
    private var fullscreenChromeApplied = false
    private var hasActiveDownloads = false
    private var ytDlpUpdateInProgress = false
    private var ytDlpUpdateMessage: String? = null
    private var currentDownloads: List<DownloadEntity> = emptyList()
    private var currentScreen = PrimaryScreen.HOME
    private var backInvokedCallback: OnBackInvokedCallback? = null
    private val settingsPreferences: SharedPreferences
        get() = getSharedPreferences(SETTINGS_PREFS_NAME, MODE_PRIVATE)
    private val recentDownloadsStore: RecentDownloadsStore
        get() = RecentDownloadsStore(settingsPreferences)
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
            showPlayer = ::showPlayer,
            startPlaybackAt = { index -> startPlaybackAt(index) },
            openExternal = { download -> fileActionsController.open(download) },
            formatLabelProvider = ::formatLabelForDetails
        )
    private val playerListController: PlayerListController
        get() = PlayerListController()
    private val downloadDetailsDialogController: DownloadDetailsDialogController
        get() = DownloadDetailsDialogController(
            activity = this,
            statusLabelProvider = ::downloadStatusLabel,
            formatLabelProvider = ::formatLabelForDetails,
            progressLabelProvider = ::progressLabelForDetails,
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
            showInvalidUrl = { message -> homeController.showError(message) },
            invalidUrlMessageProvider = { getString(R.string.invalid_url) },
            addRecentDownloadUrl = ::addRecentDownloadUrl,
            openQualityPicker = ::openYtDlpQualityPicker,
            startDownload = ::startQueuedDownload
        )
    private val mainHeaderController: MainHeaderController
        get() = MainHeaderController(
            currentScreenProvider = { currentScreen },
            focusHomeUrlInput = homeController::focusUrlInput,
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
        homeController = HomeController(
            urlInput = findViewById(R.id.urlInput),
            downloadButton = findViewById(R.id.downloadButton),
            errorText = findViewById(R.id.urlErrorText)
        )
        clipboardLinkPromptController = ClipboardLinkPromptController(
            activity = this,
            onUseUrl = { url ->
                showHome()
                homeController.setUrl(url)
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
        recentDownloadsSection = findViewById(R.id.recentDownloadsSection)
        clearRecentButton = findViewById(R.id.clearRecentButton)
        recentDownloadsList = findViewById(R.id.recentDownloadsList)
        homeRecentDownloadsSection = findViewById(R.id.homeRecentDownloadsSection)
        homeRecentDownloadsList = findViewById(R.id.homeRecentDownloadsList)
        homeRecentDownloadsRenderer = HomeRecentDownloadsRenderer(
            context = this,
            section = homeRecentDownloadsSection,
            list = homeRecentDownloadsList,
            formatLabelProvider = ::formatLabelForDetails,
            statusLabelProvider = { download -> downloadStatusLabel(download.status) },
            sizeTextProvider = ::summaryDownloadSizeText,
            badgeLabelProvider = ::downloadTypeBadgeLabel,
            onItemClick = ::showDownloadDetailsDialog
        )
        homeTabButton = findViewById(R.id.homeTabButton)
        downloadsTabButton = findViewById(R.id.downloadsTabButton)
        playerTabButton = findViewById(R.id.playerTabButton)
        mainNavigationController = MainNavigationController(
            homeContainer = homeContainer,
            downloadsContainer = downloadsContainer,
            playerContainer = playerContainer,
            homeTabButton = homeTabButton,
            downloadsTabButton = downloadsTabButton,
            playerTabButton = playerTabButton
        )
        defaultQualityValueText = findViewById(R.id.defaultQualityValueText)
        defaultQualityButton = findViewById(R.id.defaultQualityButton)
        playerMusicChip = findViewById(R.id.playerMusicChip)
        playerVideoChip = findViewById(R.id.playerVideoChip)
        playerVideoFrame = findViewById(R.id.playerVideoFrame)
        playerArtworkPlaceholder = findViewById(R.id.playerArtworkPlaceholder)
        playerVideoView = findViewById(R.id.playerVideoView)
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
            formatLabelProvider = ::formatLabelForDetails,
            onItemClick = { index -> startPlaybackAt(index) }
        )
        videoFullscreenOverlay = findViewById(R.id.videoFullscreenOverlay)
        videoFullscreenControls = findViewById(R.id.videoFullscreenControls)
        videoFullscreenCloseButton = findViewById(R.id.videoFullscreenCloseButton)
        videoFullscreenTitleText = findViewById(R.id.videoFullscreenTitleText)
        videoFullscreenView = findViewById(R.id.videoFullscreenView)
        videoFullscreenSeekBar = findViewById(R.id.videoFullscreenSeekBar)
        videoFullscreenCurrentTimeText = findViewById(R.id.videoFullscreenCurrentTimeText)
        videoFullscreenDurationText = findViewById(R.id.videoFullscreenDurationText)
        videoFullscreenPlayPauseButton = findViewById(R.id.videoFullscreenPlayPauseButton)
        playerControlsController = PlayerControlsController(
            playerMusicChip = playerMusicChip,
            playerVideoChip = playerVideoChip,
            playerVideoView = playerVideoView,
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

        settingsController = SettingsController(
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
            callbacks = SettingsController.Callbacks(
                onChooseDownloadLocation = ::chooseDownloadLocation,
                onUseDefaultDownloadLocation = ::useDefaultDownloadLocation,
                onUpdateYtDlp = ::updateYtDlpManually,
                onToggleAutoUpdateYtDlp = ::toggleAutoUpdateYtDlp,
                onDiagnostics = ::showDiagnosticsDialog,
                onAbout = ::showAboutDialog,
                onCloseSettings = ::closeSettingsOverlay
            )
        )

        homeTabButton.setOnClickListener { showHome() }
        downloadsTabButton.setOnClickListener { showDownloads() }
        playerTabButton.setOnClickListener { showPlayer() }
        settingsMenuButton.setOnClickListener { showSettings() }
        headerSearchButton.setOnClickListener { handleHeaderSearchClick() }
        defaultQualityButton.setOnClickListener { showDefaultQualityDialog() }
        clearFinishedButton.setOnClickListener { showClearFinishedDownloadsDialog() }
        clearRecentButton.setOnClickListener {
            recentDownloadsStore.clear()
            renderRecentDownloads()
        }
        homeController.onDownloadClick = onDownloadClick@{ rawUrl ->
            homeDownloadRequestController.handleDownloadRequest(
                rawUrl = rawUrl,
                homeController = homeController
            )
        }

        scope.launch {
            app.container.repository.observeDownloads().collectLatest { downloads ->
                currentDownloads = downloads
                downloadsController.submitDownloads(downloads)
                renderHomeRecentDownloads()
                renderPlayerList()
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
        clipboardLinkPromptController.maybePrompt(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
        clipboardLinkPromptController.maybePrompt(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_DOWNLOAD_TREE) return
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val flags = data.flags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
            DownloadDestinationResolver.setCustomTreeUri(this, uri)
            updateDownloadLocationText()
            YtDlpDiagnostics.record(
                context = this,
                url = "app",
                option = "destino",
                attempt = "escolher pasta",
                result = "pasta customizada escolhida",
                error = DownloadDestinationResolver.summarizeUri(uri)
            )
            showToast(getString(R.string.download_location_saved))
        } catch (exception: SecurityException) {
            showToast(getString(R.string.download_custom_folder_access_error))
            YtDlpDiagnostics.record(
                context = this,
                url = "app",
                option = "destino",
                attempt = "escolher pasta",
                result = "falha ao persistir permissao",
                error = exception.message
            )
        }
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

    private fun showHome() {
        currentScreen = PrimaryScreen.HOME
        downloadsController.hideSearch(clearQuery = true)
        appHeader.visibility = View.VISIBLE
        mainTabBar.visibility = View.VISIBLE
        mainNavigationController.showPrimaryScreen(PrimaryScreen.HOME)
        settingsController.hide()
        renderHomeRecentDownloads()
        renderRecentDownloads()
    }

    private fun showDownloads() {
        currentScreen = PrimaryScreen.DOWNLOADS
        appHeader.visibility = View.VISIBLE
        mainTabBar.visibility = View.VISIBLE
        mainNavigationController.showPrimaryScreen(PrimaryScreen.DOWNLOADS)
        settingsController.hide()
        downloadsController.setFilter(DownloadsFilter.ALL, refreshOnly = true)
        downloadsController.hideSearch(clearQuery = true)
    }

    private fun showClearFinishedDownloadsDialog() {
        DarkDialogFactory.showMessageDialog(
            this,
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

    private fun setupPlayer() {
        playerMusicChip.setOnClickListener { setPlayerCategory(PlayerCategory.MUSIC) }
        playerVideoChip.setOnClickListener { setPlayerCategory(PlayerCategory.VIDEO) }
        playerPlayPauseButton.setOnClickListener { togglePlayback() }
        playerPreviousButton.setOnClickListener { playAdjacent(offset = -1) }
        playerNextButton.setOnClickListener { playAdjacent(offset = 1) }
        playerFullscreenButton.setOnClickListener { openCurrentVideoFullscreen() }
        playerVideoFrame.setOnClickListener { toggleInlineFullscreenButton() }
        videoFullscreenCloseButton.setOnClickListener { closeVideoFullscreen(restoreInline = true) }
        videoFullscreenPlayPauseButton.setOnClickListener { togglePlayback() }
        fullscreenGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                toggleFullscreenControls()
                return true
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                val width = videoFullscreenOverlay.width.takeIf { it > 0 } ?: return true
                seekFullscreenBy(
                    deltaMs = if (event.x < width / 2f) -10_000 else 10_000
                )
                showFullscreenControls()
                scheduleFullscreenControlsAutoHide()
                return true
            }
        })
        videoFullscreenOverlay.setOnTouchListener { _, event ->
            fullscreenGestureDetector.onTouchEvent(event)
            true
        }
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
        playerVideoView.setOnCompletionListener { handlePlaybackCompleted() }
        playerVideoView.setOnErrorListener { _, _, _ ->
            if (activeVideoMode != ActiveVideoMode.INLINE) return@setOnErrorListener true
            showPlaybackErrorAndMaybeSkip()
            true
        }
        videoFullscreenView.setOnCompletionListener {
            if (activeVideoMode != ActiveVideoMode.FULLSCREEN || !isVideoFullscreenOpen()) {
                return@setOnCompletionListener
            }
            playbackHandler.removeCallbacksAndMessages(null)
            fullscreenVideoPrepared = false
            updatePlaybackButtons()
            updateNowPlayingInfo()
            updatePlaybackProgress()
        }
        videoFullscreenView.setOnErrorListener { _, _, _ ->
            if (activeVideoMode != ActiveVideoMode.FULLSCREEN || !isVideoFullscreenOpen()) {
                return@setOnErrorListener true
            }
            showToast(getString(R.string.player_playback_error))
            closeVideoFullscreen(restoreInline = false)
            true
        }
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
                DownloadOpenRouter.matchesPlayerCategory(download, category, ::formatLabelForDetails)
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
        playerVideoView.visibility = View.GONE
        audioPrepared = false
        audioPlayer = MediaPlayer().apply {
            setOnPreparedListener { player ->
                audioPrepared = true
                player.start()
                updatePlaybackButtons()
                updateNowPlayingInfo()
                updatePlaybackProgress()
                scheduleInlineFullscreenAutoHide()
            }
            setOnCompletionListener { handlePlaybackCompleted() }
            setOnErrorListener { _, _, _ ->
                handlePlaybackStartFailure(index, skipBudget)
                true
            }
            try {
                setDataSource(this@MainActivity, uri)
                prepareAsync()
            } catch (exception: Exception) {
                release()
                audioPlayer = null
                handlePlaybackStartFailure(index, skipBudget)
            }
        }
    }

    private fun startVideoPlayback(uri: Uri, index: Int, skipBudget: Int) {
        stopAudioPlayback()
        closeVideoFullscreen(restoreInline = false)
        playerArtworkPlaceholder.visibility = View.GONE
        playerVideoView.visibility = View.VISIBLE
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
        videoPrepared = false
        try {
            playerVideoView.setOnPreparedListener {
                if (activeVideoMode != ActiveVideoMode.INLINE) {
                    return@setOnPreparedListener
                }
                videoPrepared = true
                playerVideoView.setVideoSize(it.videoWidth, it.videoHeight)
                playerVideoView.visibility = View.VISIBLE
                if (positionMs > 0) {
                    playerVideoView.seekTo(positionMs)
                }
                if (playWhenReady) {
                    playerVideoView.start()
                }
                playerArtworkPlaceholder.visibility = View.GONE
                playerVideoView.visibility = View.VISIBLE
                updatePlaybackButtons()
                updateNowPlayingInfo()
                updatePlaybackProgress()
            }
            playerVideoView.setOnErrorListener { _, _, _ ->
                if (activeVideoMode != ActiveVideoMode.INLINE) {
                    return@setOnErrorListener true
                }
                onError?.invoke() ?: showPlaybackErrorAndMaybeSkip()
                true
            }
            playerVideoView.setVideoURI(uri)
        } catch (exception: Exception) {
            onError?.invoke()
        }
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
            playerVideoView.visibility == View.VISIBLE &&
            !isVideoFullscreenOpen()
    }

    private fun handlePlaybackStartFailure(index: Int, skipBudget: Int) {
        showToast(getString(R.string.player_playback_error))
        if (index + 1 < playerItems.size && skipBudget > 1) {
            startPlaybackAt(index + 1, skipBudget - 1)
        } else {
            stopCurrentPlayback(clearSelection = true)
        }
    }

    private fun showPlaybackErrorAndMaybeSkip() {
        val index = currentPlayerIndex
        showToast(getString(R.string.player_playback_error))
        if (index >= 0 && index + 1 < playerItems.size) {
            startPlaybackAt(index + 1)
        } else {
            stopCurrentPlayback(clearSelection = true)
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
        if (audioPlayer?.isPlaying == true) {
            audioPlayer?.pause()
        }
        if (activeVideoMode == ActiveVideoMode.INLINE && playerVideoView.isPlaying) {
            playerVideoView.pause()
        }
        if (activeVideoMode == ActiveVideoMode.FULLSCREEN && videoFullscreenView.isPlaying) {
            videoFullscreenView.pause()
        }
        playbackHandler.removeCallbacksAndMessages(null)
        inlineControlsHandler.removeCallbacksAndMessages(null)
        if (isVideoFullscreenOpen()) {
            showFullscreenControls()
            fullscreenControlsHandler.removeCallbacksAndMessages(null)
        }
        updatePlaybackButtons()
        updateNowPlayingInfo()
    }

    private fun resumeCurrentPlayback() {
        if (playerCategory == PlayerCategory.MUSIC) {
            if (audioPrepared) audioPlayer?.start()
        } else {
            if (activeVideoMode == ActiveVideoMode.FULLSCREEN) {
                if (fullscreenVideoPrepared) videoFullscreenView.start()
            } else if (activeVideoMode == ActiveVideoMode.INLINE) {
                if (videoPrepared) playerVideoView.start()
            }
        }
        updatePlaybackButtons()
        updateNowPlayingInfo()
        updatePlaybackProgress()
        scheduleFullscreenControlsAutoHide()
        scheduleInlineFullscreenAutoHide()
    }

    private fun stopCurrentPlayback(clearSelection: Boolean) {
        playbackHandler.removeCallbacksAndMessages(null)
        inlineControlsHandler.removeCallbacksAndMessages(null)
        clearFullscreenControlCallbacks()
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
            playerVideoView.visibility = View.GONE
            inlineFullscreenVisible = false
            updatePlayerCategoryUi()
        }
        updateNowPlayingInfo()
        updatePlaybackButtons()
    }

    private fun stopAudioPlayback() {
        runCatching { audioPlayer?.release() }
        audioPlayer = null
        audioPrepared = false
    }

    private fun stopVideoPlayback() {
        runCatching { playerVideoView.stopPlayback() }
        runCatching { playerVideoView.suspend() }
        playerVideoView.visibility = View.GONE
        videoPrepared = false
        if (activeVideoMode == ActiveVideoMode.INLINE) {
            activeVideoMode = ActiveVideoMode.NONE
        }
    }

    private fun stopFullscreenVideoPlayback() {
        runCatching { videoFullscreenView.stopPlayback() }
        runCatching { videoFullscreenView.suspend() }
        fullscreenVideoPrepared = false
        fullscreenUserSeeking = false
        if (activeVideoMode == ActiveVideoMode.FULLSCREEN) {
            activeVideoMode = ActiveVideoMode.NONE
        }
    }

    private fun releasePlayer() {
        inlineControlsHandler.removeCallbacksAndMessages(null)
        clearFullscreenControlCallbacks()
        stopCurrentPlayback(clearSelection = true)
    }

    private fun playAdjacent(offset: Int) {
        if (playerItems.isEmpty()) return
        val nextIndex = if (currentPlayerIndex < 0) {
            0
        } else {
            (currentPlayerIndex + offset).coerceIn(0, playerItems.lastIndex)
        }
        if (nextIndex != currentPlayerIndex || currentPlayerIndex < 0) {
            startPlaybackAt(nextIndex)
        }
    }

    private fun handlePlaybackCompleted() {
        if (currentPlayerIndex + 1 < playerItems.size) {
            startPlaybackAt(currentPlayerIndex + 1)
        } else {
            stopCurrentPlayback(clearSelection = false)
            updatePlaybackButtons()
            updateNowPlayingInfo()
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
            playerControlsController.updateNowPlaying(
                title = getString(R.string.player_nothing_selected),
                subtitle = getString(R.string.player_select_file),
                meta = getString(R.string.player_status_stopped)
            )
            return
        }

        val formatLabel = formatLabelForDetails(current)
        playerControlsController.updateNowPlaying(
            title = current.fileName,
            subtitle = "${playerTypeLabel(current)} - $formatLabel",
            meta = "${playbackStatusLabel()} - ${playerCurrentTimeText.text}/${playerDurationText.text}"
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
        val label = formatLabelForDetails(download).uppercase(Locale.US)
        return when {
            "MP3" in label || download.finalFileExtension().equals("mp3", ignoreCase = true) -> "MP3"
            "MP4" in label || download.finalFileExtension().equals("mp4", ignoreCase = true) -> "MP4"
            else -> downloadTypeBadgeLabel(download, label)
        }
    }

    private fun DownloadEntity.finalFileExtension(): String {
        val uriPath = destinationUri
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { Uri.parse(it).lastPathSegment }.getOrNull() }
            .orEmpty()
        return listOf(fileName, uriPath)
            .firstNotNullOfOrNull { name ->
                name.substringAfterLast('.', missingDelimiterValue = "")
                    .takeIf { it.isNotBlank() }
            }
            .orEmpty()
    }

    private fun currentPlayingDownload(): DownloadEntity? {
        return playerItems.getOrNull(currentPlayerIndex)
    }

    private fun isCurrentPlaybackRunning(): Boolean {
        return if (playerCategory == PlayerCategory.MUSIC) {
            audioPlayer?.isPlaying == true
        } else if (activeVideoMode == ActiveVideoMode.FULLSCREEN) {
            videoFullscreenView.isPlaying
        } else if (activeVideoMode == ActiveVideoMode.INLINE) {
            playerVideoView.isPlaying
        } else {
            false
        }
    }

    private fun isCurrentPlaybackPrepared(): Boolean {
        return if (playerCategory == PlayerCategory.MUSIC) {
            audioPrepared && audioPlayer != null
        } else if (activeVideoMode == ActiveVideoMode.FULLSCREEN) {
            fullscreenVideoPrepared
        } else if (activeVideoMode == ActiveVideoMode.INLINE) {
            videoPrepared
        } else {
            false
        }
    }

    private fun currentDuration(): Int {
        return runCatching {
            if (playerCategory == PlayerCategory.MUSIC) {
                if (audioPrepared) audioPlayer?.duration ?: 0 else 0
            } else if (activeVideoMode == ActiveVideoMode.FULLSCREEN) {
                if (fullscreenVideoPrepared) videoFullscreenView.duration.takeIf { it > 0 } ?: 0 else 0
            } else if (activeVideoMode == ActiveVideoMode.INLINE) {
                if (videoPrepared) playerVideoView.duration.takeIf { it > 0 } ?: 0 else 0
            } else {
                0
            }
        }.getOrDefault(0)
    }

    private fun currentPosition(): Int {
        return runCatching {
            if (playerCategory == PlayerCategory.MUSIC) {
                if (audioPrepared) audioPlayer?.currentPosition ?: 0 else 0
            } else if (activeVideoMode == ActiveVideoMode.FULLSCREEN) {
                if (fullscreenVideoPrepared) videoFullscreenView.currentPosition else 0
            } else if (activeVideoMode == ActiveVideoMode.INLINE) {
                if (videoPrepared) playerVideoView.currentPosition else 0
            } else {
                0
            }
        }.getOrDefault(0)
    }

    private fun seekCurrentPlayback(positionMs: Int) {
        runCatching {
            if (playerCategory == PlayerCategory.MUSIC) {
                if (audioPrepared) audioPlayer?.seekTo(positionMs)
            } else if (activeVideoMode == ActiveVideoMode.FULLSCREEN) {
                if (fullscreenVideoPrepared) videoFullscreenView.seekTo(positionMs)
            } else if (activeVideoMode == ActiveVideoMode.INLINE) {
                if (videoPrepared) playerVideoView.seekTo(positionMs)
            }
        }
    }

    private fun openCurrentVideoFullscreen() {
        if (playerCategory != PlayerCategory.VIDEO) return
        val download = currentPlayingDownload() ?: return
        val playbackUri = resolvePlaybackUri(download)

        if (playbackUri == null) {
            showToast(getString(R.string.player_playback_error))
            return
        }

        val position = currentPosition()
        val wasPlaying = isCurrentPlaybackRunning()
        playbackHandler.removeCallbacksAndMessages(null)
        inlineControlsHandler.removeCallbacksAndMessages(null)
        inlineFullscreenVisible = false
        stopVideoPlayback()
        playerVideoView.visibility = View.GONE
        playerArtworkPlaceholder.visibility = View.VISIBLE
        videoFullscreenTitleText.text = download.fileName
        playerControlsController.updateFullscreenProgress(
            progress = 0,
            currentTime = PlayerProgressCalculator.formatPlaybackTime(position),
            duration = playerDurationText.text
        )
        enterVideoFullscreenChrome()
        videoFullscreenOverlay.visibility = View.VISIBLE
        showFullscreenControls()
        activeVideoMode = ActiveVideoMode.FULLSCREEN
        fullscreenVideoPrepared = false
        try {
            videoFullscreenView.setOnPreparedListener {
                if (activeVideoMode != ActiveVideoMode.FULLSCREEN || !isVideoFullscreenOpen()) {
                    return@setOnPreparedListener
                }
                fullscreenVideoPrepared = true
                videoFullscreenView.setVideoSize(it.videoWidth, it.videoHeight)
                if (position > 0) {
                    videoFullscreenView.seekTo(position)
                }
                if (wasPlaying) {
                    videoFullscreenView.start()
                }
                updatePlaybackButtons()
                updateNowPlayingInfo()
                updatePlaybackProgress()
                scheduleFullscreenControlsAutoHide()
            }
            videoFullscreenView.setVideoURI(playbackUri)
        } catch (exception: Exception) {
            showToast(getString(R.string.player_playback_error))
            closeVideoFullscreen(restoreInline = false)
        }
    }

    private fun closeVideoFullscreen(restoreInline: Boolean) {
        if (!::videoFullscreenOverlay.isInitialized || !isVideoFullscreenOpen()) return
        val download = currentPlayingDownload()
        val position = currentPosition()
        val wasPlaying = isCurrentPlaybackRunning()
        playbackHandler.removeCallbacksAndMessages(null)
        stopFullscreenVideoPlayback()
        videoFullscreenOverlay.visibility = View.GONE
        clearFullscreenControlCallbacks()
        exitVideoFullscreenChrome()
        playerControlsController.resetFullscreenProgress(getString(R.string.player_time_zero))

        if (restoreInline && playerCategory == PlayerCategory.VIDEO && download != null) {
            val playbackUri = resolvePlaybackUri(download)
            if (playbackUri == null) {
                showToast(getString(R.string.player_playback_error))
                stopCurrentPlayback(clearSelection = true)
                return
            }
            playerArtworkPlaceholder.visibility = View.GONE
            playerVideoView.visibility = View.VISIBLE
            prepareInlineVideo(
                uri = playbackUri,
                positionMs = position,
                playWhenReady = wasPlaying,
                onError = {
                    showToast(getString(R.string.player_playback_error))
                    stopCurrentPlayback(clearSelection = true)
                }
            )
        } else {
            updatePlaybackButtons()
            updateNowPlayingInfo()
        }
    }

    private fun isVideoFullscreenOpen(): Boolean {
        return ::videoFullscreenOverlay.isInitialized && videoFullscreenOverlay.visibility == View.VISIBLE
    }

    private fun showFullscreenControls() {
        fullscreenControlsVisible = true
        videoFullscreenControls.visibility = View.VISIBLE
    }

    private fun hideFullscreenControls() {
        if (!isCurrentPlaybackRunning()) return
        fullscreenControlsVisible = false
        videoFullscreenControls.visibility = View.GONE
    }

    private fun toggleFullscreenControls() {
        if (fullscreenControlsVisible) {
            hideFullscreenControls()
        } else {
            showFullscreenControls()
            scheduleFullscreenControlsAutoHide()
        }
    }

    private fun scheduleFullscreenControlsAutoHide() {
        fullscreenControlsHandler.removeCallbacksAndMessages(null)
        if (!isVideoFullscreenOpen() || !isCurrentPlaybackRunning()) return
        fullscreenControlsHandler.postDelayed({ hideFullscreenControls() }, 3_000L)
    }

    private fun clearFullscreenControlCallbacks() {
        fullscreenControlsHandler.removeCallbacksAndMessages(null)
        fullscreenFeedbackHandler.removeCallbacksAndMessages(null)
        if (::videoFullscreenSeekFeedbackText.isInitialized) {
            videoFullscreenSeekFeedbackText.visibility = View.GONE
        }
    }

    private fun seekFullscreenBy(deltaMs: Int) {
        if (!isVideoFullscreenOpen() || !fullscreenVideoPrepared) return
        val duration = currentDuration()
        if (duration <= 0) return
        val target = PlayerProgressCalculator.seekBy(
            currentPositionMs = currentPosition(),
            deltaMs = deltaMs,
            durationMs = duration
        )
        seekCurrentPlayback(target)
        updatePlaybackProgress()
        showFullscreenSeekFeedback(
            if (deltaMs < 0) getString(R.string.player_rewind_10) else getString(R.string.player_forward_10)
        )
    }

    private fun showFullscreenSeekFeedback(text: String) {
        videoFullscreenSeekFeedbackText.text = text
        videoFullscreenSeekFeedbackText.visibility = View.VISIBLE
        fullscreenFeedbackHandler.removeCallbacksAndMessages(null)
        fullscreenFeedbackHandler.postDelayed({
            videoFullscreenSeekFeedbackText.visibility = View.GONE
        }, 700L)
    }

    private fun enterVideoFullscreenChrome() {
        if (fullscreenChromeApplied) return
        fullscreenChromeApplied = true
        previousRequestedOrientation = requestedOrientation
        previousSystemUiVisibility = window.decorView.systemUiVisibility
        appHeader.visibility = View.GONE
        mainTabBar.visibility = View.GONE
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    private fun exitVideoFullscreenChrome() {
        if (!fullscreenChromeApplied) return
        fullscreenChromeApplied = false
        requestedOrientation = previousRequestedOrientation
        window.decorView.systemUiVisibility = previousSystemUiVisibility
        if (!settingsController.isVisible()) {
            appHeader.visibility = View.VISIBLE
            mainTabBar.visibility = View.VISIBLE
        }
    }

    private fun resolvePlaybackUri(download: DownloadEntity): Uri? {
        val destination = download.destinationUri?.takeIf { it.isNotBlank() } ?: return null
        val destinationUri = Uri.parse(destination)
        return when (destinationUri.scheme) {
            "content" -> if (canOpenContentUri(destinationUri)) destinationUri else null
            "file" -> {
                val file = File(destinationUri.path ?: return null)
                if (file.exists()) Uri.fromFile(file) else null
            }
            null -> {
                val file = File(destination)
                if (file.exists()) Uri.fromFile(file) else null
            }
            else -> null
        }
    }

    private fun canOpenContentUri(uri: Uri): Boolean {
        return runCatching {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } == true
        }.getOrDefault(false)
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
        val downloads = currentDownloads.take(MAX_HOME_RECENT_DOWNLOADS_DISPLAYED)
        homeRecentDownloadsRenderer.render(downloads)
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
        val recentUrls = recentDownloadsStore.load()
        recentDownloadsSection.visibility = if (recentUrls.isEmpty()) View.GONE else View.VISIBLE
        clearRecentButton.visibility = if (recentUrls.isEmpty()) View.GONE else View.VISIBLE
        recentDownloadsList.removeAllViews()

        recentUrls.take(RecentDownloadsStore.MAX_RECENT_DOWNLOAD_URLS_DISPLAYED).forEachIndexed { index, url ->
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
        recentDownloadsStore.add(url)
    }

    private fun showDownloadDetailsDialog(download: DownloadEntity) {
        downloadDetailsDialogController.show(download)
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

    private fun showPlayer() {
        currentScreen = PrimaryScreen.PLAYER
        downloadsController.hideSearch(clearQuery = true)
        appHeader.visibility = View.VISIBLE
        mainTabBar.visibility = View.VISIBLE
        mainNavigationController.showPrimaryScreen(PrimaryScreen.PLAYER)
        settingsController.hide()
        renderPlayerList()
    }

    private fun showSettings(scrollToDownloadLocation: Boolean = false) {
        downloadsController.hideSearch(clearQuery = false)
        appHeader.visibility = View.GONE
        mainTabBar.visibility = View.GONE
        homeContainer.visibility = View.GONE
        playerContainer.visibility = View.GONE
        downloadsContainer.visibility = View.GONE
        updateDefaultQualityText()
        updateDownloadLocationText()
        updateYtDlpUpdateUiState()
        updateAutoUpdateYtDlpUiState()
        updateSelectedTab(null)
        settingsController.show(scrollToDownloadLocation)
    }

    private fun closeSettingsOverlay() {
        when (currentScreen) {
            PrimaryScreen.HOME -> showHome()
            PrimaryScreen.DOWNLOADS -> showDownloads()
            PrimaryScreen.PLAYER -> showPlayer()
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
        if (settingsController.isVisible()) {
            closeSettingsOverlay()
            return true
        }
        return false
    }

    private fun updateDownloadLocationText() {
        val customUri = DownloadDestinationResolver.customTreeUri(this)
        val locationText = if (customUri != null) {
            getString(
                R.string.download_location_selected,
                DownloadDestinationResolver.summarizeUri(customUri)
            )
        } else {
            getString(
                R.string.download_location_default,
                DownloadDestinationResolver.defaultDestinationLabel()
            )
        }
        settingsController.updateDownloadLocationText(locationText)
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

        DarkDialogFactory.showMessageDialog(
            this,
            title = getString(R.string.about_dialog_title),
            message = message,
            buttons = listOf(DarkDialogButton(getString(android.R.string.ok), primary = true))
        )
    }

    private fun showDiagnosticsDialog() {
        diagnosticsController.show()
    }

    private fun chooseDownloadLocation() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_DOWNLOAD_TREE)
    }

    private fun useDefaultDownloadLocation() {
        DownloadDestinationResolver.clearCustomTreeUri(this)
        updateDownloadLocationText()
        YtDlpDiagnostics.record(
            context = this,
            url = "app",
            option = "destino",
            attempt = "restaurar padrao",
            result = "pasta padrao restaurada"
        )
        showToast(getString(R.string.download_location_default_restored))
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
        settingsController.setYtDlpUpdateState(
            isInProgress = ytDlpUpdateInProgress,
            hasActiveDownloads = hasActiveDownloads,
            message = ytDlpUpdateMessage,
            inProgressText = getString(R.string.update_ytdlp_in_progress),
            busyText = getString(R.string.update_ytdlp_busy)
        )
    }

    private fun toggleAutoUpdateYtDlp() {
        settingsPreferencesStore.toggleAutoUpdateYtDlpOnYoutubeErrors()
        updateAutoUpdateYtDlpUiState()
    }

    private fun updateAutoUpdateYtDlpUiState() {
        val enabled = settingsPreferencesStore.isAutoUpdateYtDlpOnYoutubeErrorsEnabled()
        settingsController.setAutoUpdateEnabled(
            isEnabled = enabled,
            enabledText = getString(R.string.auto_update_ytdlp_enabled),
            disabledText = getString(R.string.auto_update_ytdlp_disabled)
        )
    }

    private fun updateSelectedTab(selectedTab: Button?) {
        listOf(homeTabButton, downloadsTabButton, playerTabButton).forEach { tab ->
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
        val sharedUrl = SharedTextUrlExtractor.extract(
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
        if (!DownloadSourceClassifier.shouldUseHttpDownloader(sharedUrl)) {
            quickDownloadSheetController.show(
                url = sharedUrl,
                options = downloadQualityOptions()
            ) { option ->
                startQueuedDownload(
                    url = sharedUrl,
                    qualitySelector = option.formatSelector,
                    homeController = homeController
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
                showDownloads()
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
        val options = defaultQualityOptions()
        val currentIndex = options.indexOfFirst {
            it.preferenceValue == selectedDefaultQualityOption().preferenceValue
        }.coerceAtLeast(0)
        qualityDialogController.showDefaultQualityDialog(options, currentIndex) { option ->
            saveDefaultQualityOption(option)
            updateDefaultQualityText()
        }
    }

    private fun updateDefaultQualityText() {
        defaultQualityValueText.text = getString(
            R.string.default_quality_selected,
            selectedDefaultQualityOption().label
        )
    }

    private fun selectedDefaultQualityOption(): QualityOptionUi {
        val savedValue = defaultQualityPreferences.load()
        return defaultQualityOptions().firstOrNull { it.preferenceValue == savedValue }
            ?: defaultQualityOptions().first()
    }

    private fun saveDefaultQualityOption(option: QualityOptionUi) {
        defaultQualityPreferences.save(option.preferenceValue)
    }

    private fun defaultQualityOptions(): List<QualityOptionUi> {
        return listOf(
            QualityOptionUi(
                label = getString(R.string.default_quality_ask_always),
                preferenceValue = DefaultQualityPreferences.DEFAULT_QUALITY_ASK_VALUE,
                formatSelector = null
            )
        ) + downloadQualityOptions()
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

    override fun onBackPressed() {
        if (handleBackNavigation()) {
            return
        }
        super.onBackPressed()
    }

    private fun normalizeMimeType(mimeType: String?): String? {
        return mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.takeIf { it.contains('/') }
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

    companion object {
        const val EXTRA_OPEN_DOWNLOADS = "com.androiddownload.extra.OPEN_DOWNLOADS"
        const val EXTRA_OPEN_DOWNLOAD_ID = "com.androiddownload.extra.OPEN_DOWNLOAD_ID"
        private const val SETTINGS_PREFS_NAME = "aio_downloader_settings"
        private const val MAX_HOME_RECENT_DOWNLOADS_DISPLAYED = 4
        private const val REQUEST_DOWNLOAD_TREE = 2002
    }
}
