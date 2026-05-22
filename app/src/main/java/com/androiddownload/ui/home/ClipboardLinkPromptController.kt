package com.androiddownload.ui.home

import android.app.Activity
import android.content.Intent
import com.androiddownload.R
import com.androiddownload.core.utils.ClipboardUrlReader
import com.androiddownload.ui.common.DarkDialogButton
import com.androiddownload.ui.common.DarkDialogFactory

class ClipboardLinkPromptController(
    private val activity: Activity,
    private val onUseUrl: (String) -> Unit
) {
    private var lastClipboardUrlPrompted: String? = null

    fun maybePrompt(intent: Intent?) {
        if (!isLauncherIntent(intent)) return

        activity.window.decorView.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            val clipboardUrl = ClipboardUrlReader(activity).readUrl() ?: return@post
            if (clipboardUrl == lastClipboardUrlPrompted) return@post

            lastClipboardUrlPrompted = clipboardUrl
            showClipboardUrlDialog(clipboardUrl)
        }
    }

    private fun showClipboardUrlDialog(url: String) {
        DarkDialogFactory.showMessageDialog(
            activity = activity,
            title = activity.getString(R.string.clipboard_url_title),
            message = buildClipboardUrlMessage(url),
            buttons = listOf(
                DarkDialogButton(activity.getString(R.string.clipboard_url_ignore)),
                DarkDialogButton(activity.getString(R.string.clipboard_url_use), primary = true) {
                    onUseUrl(url)
                }
            )
        )
    }

    private fun buildClipboardUrlMessage(url: String): String {
        val displayUrl = if (url.length > CLIPBOARD_URL_PREVIEW_MAX_LENGTH) {
            url.take(CLIPBOARD_URL_PREVIEW_MAX_LENGTH - 1) + "..."
        } else {
            url
        }
        return "${activity.getString(R.string.clipboard_url_message)}\n\n$displayUrl"
    }

    private fun isLauncherIntent(intent: Intent?): Boolean {
        return intent?.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_LAUNCHER)
    }

    private companion object {
        private const val CLIPBOARD_URL_PREVIEW_MAX_LENGTH = 96
    }
}
