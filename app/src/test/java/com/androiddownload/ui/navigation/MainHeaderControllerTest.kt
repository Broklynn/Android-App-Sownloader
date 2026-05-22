package com.androiddownload.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class MainHeaderControllerTest {
    @Test
    fun homeFocusesUrlInputAndShowsKeyboard() {
        val events = mutableListOf<String>()
        val controller = controllerFor(
            screen = PrimaryScreen.HOME,
            events = events
        )

        controller.handleSearchClick()

        assertEquals(listOf("focus-home-url", "show-keyboard"), events)
    }

    @Test
    fun downloadsTogglesSearchOnly() {
        val events = mutableListOf<String>()
        val controller = controllerFor(
            screen = PrimaryScreen.DOWNLOADS,
            events = events
        )

        controller.handleSearchClick()

        assertEquals(listOf("toggle-downloads-search"), events)
    }

    @Test
    fun playerDoesNothing() {
        val events = mutableListOf<String>()
        val controller = controllerFor(
            screen = PrimaryScreen.PLAYER,
            events = events
        )

        controller.handleSearchClick()

        assertEquals(emptyList<String>(), events)
    }

    private fun controllerFor(
        screen: PrimaryScreen,
        events: MutableList<String>
    ): MainHeaderController {
        return MainHeaderController(
            currentScreenProvider = { screen },
            focusHomeUrlInput = { events += "focus-home-url" },
            showKeyboardForCurrentFocus = { events += "show-keyboard" },
            toggleDownloadsSearch = { events += "toggle-downloads-search" }
        )
    }
}
