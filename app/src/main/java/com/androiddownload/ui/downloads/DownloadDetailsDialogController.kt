package com.androiddownload.ui.downloads

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import com.androiddownload.R
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import com.androiddownload.ui.common.DarkDialogButton
import com.androiddownload.ui.common.DarkDialogFactory

class DownloadDetailsDialogController(
    private val activity: Activity,
    private val statusLabelProvider: (DownloadStatus) -> String,
    private val formatLabelProvider: (DownloadEntity) -> String,
    private val progressLabelProvider: (DownloadEntity) -> String,
    private val onOpen: (DownloadEntity) -> Unit,
    private val onShare: (DownloadEntity) -> Unit
) {
    fun show(download: DownloadEntity) {
        val contentView = DownloadDetailsRenderer(
            context = activity,
            statusLabelProvider = statusLabelProvider,
            formatLabelProvider = formatLabelProvider,
            progressLabelProvider = progressLabelProvider
        ).buildContent(download)

        val buttons = mutableListOf(
            DarkDialogButton(activity.getString(R.string.details_close)),
            DarkDialogButton(activity.getString(R.string.details_copy_url), primary = true) {
                copyUrl(download)
            }
        )
        if (download.status == DownloadStatus.COMPLETED) {
            buttons.add(
                DarkDialogButton(activity.getString(R.string.open)) {
                    onOpen(download)
                }
            )
            buttons.add(
                DarkDialogButton(activity.getString(R.string.details_share)) {
                    onShare(download)
                }
            )
        }

        DarkDialogFactory.showContentDialog(
            activity = activity,
            title = download.fileName,
            contentView = contentView,
            buttons = buttons
        )
    }

    private fun copyUrl(download: DownloadEntity) {
        val clipboard = activity.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(
            ClipData.newPlainText(activity.getString(R.string.details_copy_url), download.sourceUrl)
        )
        Toast.makeText(activity, activity.getString(R.string.url_copied), Toast.LENGTH_SHORT).show()
    }
}
