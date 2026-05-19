package com.androiddownload.ui.downloads

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window

class QuickDownloadSheetController(
    private val activity: Activity
) {
    private val renderer = QuickDownloadSheetRenderer(activity)
    private var dialog: Dialog? = null
    private var selectedOption: QualityOptionUi? = null

    fun show(
        url: String,
        options: List<QualityOptionUi>,
        onCanceled: () -> Unit = {},
        onSelected: (QualityOptionUi) -> Unit
    ) {
        dismiss()
        selectedOption = null

        val sheetDialog = Dialog(activity)
        var completed = false
        var canceledNotified = false
        fun notifyCanceled() {
            if (!completed && !canceledNotified) {
                canceledNotified = true
                onCanceled()
            }
        }
        val content = renderer.build(
            url = url,
            options = options,
            callbacks = QuickDownloadSheetRenderer.Callbacks(
                onSelected = { selectedOption = it },
                onDownload = {
                    selectedOption?.let { option ->
                        completed = true
                        sheetDialog.dismiss()
                        onSelected(option)
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
                selectedOption = null
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
        sheetDialog.show()
    }

    private fun dismiss() {
        dialog?.dismiss()
        dialog = null
        selectedOption = null
    }
}
