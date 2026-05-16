package com.androiddownload.ui.settings

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import com.androiddownload.R
import com.androiddownload.core.utils.YtDlpDiagnostics
import com.androiddownload.ui.common.DarkDialogButton
import com.androiddownload.ui.common.DarkDialogFactory

class DiagnosticsController(
    private val activity: Activity,
    private val onMessage: (String) -> Unit
) {
    fun show() {
        val diagnostics = YtDlpDiagnostics.formatted(activity)
        val textView = TextView(activity).apply {
            text = diagnostics
            setTextIsSelectable(true)
            setTextColor(activity.getColor(R.color.text_secondary))
            textSize = 13f
            setLineSpacing(activity.dp(2).toFloat(), 1f)
        }
        DarkDialogFactory.showContentDialog(
            activity = activity,
            title = activity.getString(R.string.diagnostics_title),
            contentView = ScrollView(activity).apply {
                addView(
                    textView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            },
            buttons = listOf(
                DarkDialogButton(activity.getString(R.string.details_close)),
                DarkDialogButton(activity.getString(R.string.diagnostics_copy), primary = true) {
                    copyDiagnostics()
                },
                DarkDialogButton(activity.getString(R.string.diagnostics_clear)) {
                    YtDlpDiagnostics.clear(activity)
                    onMessage(activity.getString(R.string.diagnostics_cleared))
                }
            )
        )
    }

    private fun copyDiagnostics() {
        val clipboard = activity.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                activity.getString(R.string.diagnostics_title),
                YtDlpDiagnostics.formatted(activity)
            )
        )
        onMessage(activity.getString(R.string.diagnostics_copied))
    }

    private fun Activity.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
