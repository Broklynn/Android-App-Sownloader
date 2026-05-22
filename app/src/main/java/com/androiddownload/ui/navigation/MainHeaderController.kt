package com.androiddownload.ui.navigation

class MainHeaderController(
    private val currentScreenProvider: () -> PrimaryScreen,
    private val focusHomeUrlInput: () -> Unit,
    private val showKeyboardForCurrentFocus: () -> Unit,
    private val toggleDownloadsSearch: () -> Unit
) {
    fun handleSearchClick() {
        when (currentScreenProvider()) {
            PrimaryScreen.HOME -> {
                focusHomeUrlInput()
                showKeyboardForCurrentFocus()
            }
            PrimaryScreen.DOWNLOADS -> toggleDownloadsSearch()
            PrimaryScreen.PLAYER -> Unit
        }
    }
}
