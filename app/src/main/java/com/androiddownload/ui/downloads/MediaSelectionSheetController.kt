package com.androiddownload.ui.downloads

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import com.androiddownload.download.media.SharedMediaItem
import com.androiddownload.download.media.SharedMediaPreview

class MediaSelectionSheetController(
    private val activity: Activity
) {
    private var dialog: Dialog? = null
    private var renderer: MediaSelectionSheetRenderer? = null

    fun show(
        preview: SharedMediaPreview,
        onDownloadSelected: (List<SharedMediaItem>) -> Unit,
        onCanceled: () -> Unit = {}
    ) {
        dismiss()

        val sheetDialog = Dialog(activity)
        val sheetRenderer = MediaSelectionSheetRenderer(activity)
        var completed = false
        var canceledNotified = false

        fun notifyCanceled() {
            if (!completed && !canceledNotified) {
                canceledNotified = true
                onCanceled()
            }
        }

        val content = sheetRenderer.build(
            preview = preview,
            callbacks = MediaSelectionSheetRenderer.Callbacks(
                onDownloadSelected = { items ->
                    if (items.isNotEmpty()) {
                        completed = true
                        sheetDialog.dismiss()
                        onDownloadSelected(items)
                    }
                },
                onDownloadAll = { items ->
                    if (items.isNotEmpty()) {
                        completed = true
                        sheetDialog.dismiss()
                        onDownloadSelected(items)
                    }
                },
                onCancel = {
                    sheetDialog.dismiss()
                    notifyCanceled()
                }
            )
        )

        sheetDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        sheetDialog.setContentView(content)
        sheetDialog.setOnCancelListener {
            notifyCanceled()
        }
        sheetDialog.setOnDismissListener {
            if (dialog === sheetDialog) {
                notifyCanceled()
                dialog = null
                renderer = null
            }
        }
        sheetDialog.setOnShowListener {
            sheetDialog.window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setDimAmount(0.58f)
                setGravity(Gravity.BOTTOM)
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }

        dialog = sheetDialog
        renderer = sheetRenderer
        sheetDialog.show()
    }

    private fun dismiss() {
        dialog?.dismiss()
        dialog = null
        renderer = null
    }
}
