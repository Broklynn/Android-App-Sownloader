package com.androiddownload.ui.common

import android.app.Activity
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager

class KeyboardController(
    private val activity: Activity
) {
    fun showForCurrentFocus() {
        val inputManager = activity.getSystemService(InputMethodManager::class.java) ?: return
        activity.currentFocus?.let { inputManager.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT) }
    }

    fun hideFrom(view: View) {
        val inputManager = activity.getSystemService(InputMethodManager::class.java) ?: return
        inputManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    fun hideFocusedSearchOnOutsideTouch(event: MotionEvent, searchFields: List<View>) {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return
        val focusedSearch = activity.currentFocus?.takeIf { focusedView ->
            searchFields.any { searchField -> searchField == focusedView }
        } ?: return
        if (isTouchInside(focusedSearch, event)) return

        focusedSearch.clearFocus()
        hideFrom(focusedSearch)
    }

    private fun isTouchInside(view: View, event: MotionEvent): Boolean {
        val bounds = Rect()
        view.getGlobalVisibleRect(bounds)
        return bounds.contains(event.rawX.toInt(), event.rawY.toInt())
    }
}
