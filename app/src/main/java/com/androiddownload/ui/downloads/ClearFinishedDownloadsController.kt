package com.androiddownload.ui.downloads

import android.app.Activity
import com.androiddownload.R
import com.androiddownload.ui.common.DarkDialogButton
import com.androiddownload.ui.common.DarkDialogFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ClearFinishedDownloadsController(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val clearFinishedDownloadsAction: suspend () -> Int,
    private val showToast: (String) -> Unit
) {
    fun showClearFinishedDownloadsDialog() {
        DarkDialogFactory.showMessageDialog(
            activity,
            title = activity.getString(R.string.clear_finished_downloads_title),
            message = activity.getString(R.string.clear_finished_downloads_message),
            buttons = listOf(
                DarkDialogButton(activity.getString(android.R.string.cancel)),
                DarkDialogButton(activity.getString(android.R.string.ok), primary = true) {
                    clearFinishedDownloads()
                }
            )
        )
    }

    private fun clearFinishedDownloads() {
        scope.launch {
            val removedCount = clearFinishedDownloadsAction()
            if (removedCount == 0) {
                showToast(activity.getString(R.string.clear_finished_downloads_empty))
            }
        }
    }
}
