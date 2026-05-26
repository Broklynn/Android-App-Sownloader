package com.androiddownload.ui.settings

import android.app.Activity
import com.androiddownload.R
import com.androiddownload.ui.common.DarkDialogButton
import com.androiddownload.ui.common.DarkDialogFactory

class SettingsInfoController(
    private val activity: Activity,
    private val diagnosticsController: DiagnosticsController
) {
    fun showAboutDialog() {
        val versionName = runCatching {
            @Suppress("DEPRECATION")
            activity.packageManager.getPackageInfo(activity.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { activity.getString(R.string.not_available) }
        val message = buildString {
            appendLine(activity.getString(R.string.about_app_name))
            appendLine(activity.getString(R.string.about_app_version, versionName))
            appendLine()
            appendLine(activity.getString(R.string.about_app_description))
            appendLine()
            append(activity.getString(R.string.about_app_responsible_use))
        }

        DarkDialogFactory.showMessageDialog(
            activity,
            title = activity.getString(R.string.about_dialog_title),
            message = message,
            buttons = listOf(DarkDialogButton(activity.getString(android.R.string.ok), primary = true))
        )
    }

    fun showDiagnosticsDialog() {
        diagnosticsController.show()
    }
}
