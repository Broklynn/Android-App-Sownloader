package com.androiddownload.ui.navigation

import android.view.View
import com.androiddownload.R

class MainNavigationController(
    private val appHeader: View,
    private val mainTabBar: View,
    private val homeContainer: View,
    private val downloadsContainer: View,
    private val playerContainer: View,
    private val settingsMenuButton: View,
    private val homeTabButton: View,
    private val downloadsTabButton: View,
    private val playerTabButton: View,
    private val hideDownloadsSearch: (clearQuery: Boolean) -> Unit,
    private val resetDownloadsFilter: () -> Unit,
    private val hideSettings: () -> Unit,
    private val refreshHome: () -> Unit,
    private val refreshSettings: (scrollToDownloadLocation: Boolean) -> Unit,
    private val renderPlayerList: () -> Unit
) {
    var currentScreen: PrimaryScreen = PrimaryScreen.HOME
        private set

    init {
        homeTabButton.setOnClickListener { showHome() }
        downloadsTabButton.setOnClickListener { showDownloads() }
        playerTabButton.setOnClickListener { showPlayer() }
        settingsMenuButton.setOnClickListener { showSettings() }
    }

    fun showHome() {
        currentScreen = PrimaryScreen.HOME
        hideDownloadsSearch(true)
        showPrimaryScreen(PrimaryScreen.HOME)
        hideSettings()
        refreshHome()
    }

    fun showDownloads() {
        currentScreen = PrimaryScreen.DOWNLOADS
        showPrimaryScreen(PrimaryScreen.DOWNLOADS)
        hideSettings()
        resetDownloadsFilter()
        hideDownloadsSearch(true)
    }

    fun showPlayer() {
        currentScreen = PrimaryScreen.PLAYER
        hideDownloadsSearch(true)
        showPrimaryScreen(PrimaryScreen.PLAYER)
        hideSettings()
        renderPlayerList()
    }

    fun showSettings(scrollToDownloadLocation: Boolean = false) {
        hideDownloadsSearch(false)
        appHeader.visibility = View.GONE
        mainTabBar.visibility = View.GONE
        homeContainer.visibility = View.GONE
        downloadsContainer.visibility = View.GONE
        playerContainer.visibility = View.GONE
        updateSelectedTab(null)
        refreshSettings(scrollToDownloadLocation)
    }

    fun closeSettingsOverlay() {
        when (currentScreen) {
            PrimaryScreen.HOME -> showHome()
            PrimaryScreen.DOWNLOADS -> showDownloads()
            PrimaryScreen.PLAYER -> showPlayer()
        }
    }

    private fun showPrimaryScreen(screen: PrimaryScreen) {
        appHeader.visibility = View.VISIBLE
        mainTabBar.visibility = View.VISIBLE
        homeContainer.visibility = if (screen == PrimaryScreen.HOME) View.VISIBLE else View.GONE
        downloadsContainer.visibility = if (screen == PrimaryScreen.DOWNLOADS) View.VISIBLE else View.GONE
        playerContainer.visibility = if (screen == PrimaryScreen.PLAYER) View.VISIBLE else View.GONE

        updateSelectedTab(
            when (screen) {
                PrimaryScreen.HOME -> homeTabButton
                PrimaryScreen.DOWNLOADS -> downloadsTabButton
                PrimaryScreen.PLAYER -> playerTabButton
            }
        )
    }

    private fun updateSelectedTab(selectedTab: View?) {
        listOf(homeTabButton, downloadsTabButton, playerTabButton).forEach { tab ->
            val isSelected = tab == selectedTab
            tab.isSelected = isSelected
            tab.setBackgroundResource(
                if (isSelected) R.drawable.bg_tab_selected else R.drawable.bg_tab_unselected
            )
        }
    }
}
