package com.androiddownload.ui.navigation

import android.view.View
import com.androiddownload.R

class MainNavigationController(
    private val appHeader: View,
    private val mainTabBar: View,
    private val homeContainer: View,
    private val downloadsContainer: View,
    private val playerContainer: View,
    private val homeTabButton: View,
    private val downloadsTabButton: View,
    private val playerTabButton: View
) {
    fun showPrimaryScreen(screen: PrimaryScreen) {
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

    fun showSettings() {
        appHeader.visibility = View.GONE
        mainTabBar.visibility = View.GONE
        homeContainer.visibility = View.GONE
        downloadsContainer.visibility = View.GONE
        playerContainer.visibility = View.GONE
        updateSelectedTab(null)
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
