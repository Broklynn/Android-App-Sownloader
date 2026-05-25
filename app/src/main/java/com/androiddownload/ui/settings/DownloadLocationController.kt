package com.androiddownload.ui.settings

import android.app.Activity
import android.content.Intent
import com.androiddownload.R
import com.androiddownload.core.utils.DownloadDestinationResolver
import com.androiddownload.core.utils.YtDlpDiagnostics

class DownloadLocationController(
    private val activity: Activity,
    private val settingsController: SettingsController,
    private val requestCode: Int,
    private val showToast: (String) -> Unit
) {
    fun chooseDownloadLocation() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        activity.startActivityForResult(intent, requestCode)
    }

    fun useDefaultDownloadLocation() {
        DownloadDestinationResolver.clearCustomTreeUri(activity)
        updateDownloadLocationText()
        YtDlpDiagnostics.record(
            context = activity,
            url = "app",
            option = "destino",
            attempt = "restaurar padrao",
            result = "pasta padrao restaurada"
        )
        showToast(activity.getString(R.string.download_location_default_restored))
    }

    fun updateDownloadLocationText() {
        val customUri = DownloadDestinationResolver.customTreeUri(activity)
        val locationText = if (customUri != null) {
            activity.getString(
                R.string.download_location_selected,
                DownloadDestinationResolver.summarizeUri(customUri)
            )
        } else {
            activity.getString(
                R.string.download_location_default,
                DownloadDestinationResolver.defaultDestinationLabel()
            )
        }
        settingsController.updateDownloadLocationText(locationText)
    }

    fun handleActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ): Boolean {
        if (requestCode != this.requestCode) return false
        if (resultCode != Activity.RESULT_OK) return true
        val uri = data?.data ?: return true
        val flags = data.flags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        try {
            activity.contentResolver.takePersistableUriPermission(uri, flags)
            DownloadDestinationResolver.setCustomTreeUri(activity, uri)
            updateDownloadLocationText()
            YtDlpDiagnostics.record(
                context = activity,
                url = "app",
                option = "destino",
                attempt = "escolher pasta",
                result = "pasta customizada escolhida",
                error = DownloadDestinationResolver.summarizeUri(uri)
            )
            showToast(activity.getString(R.string.download_location_saved))
        } catch (exception: SecurityException) {
            showToast(activity.getString(R.string.download_custom_folder_access_error))
            YtDlpDiagnostics.record(
                context = activity,
                url = "app",
                option = "destino",
                attempt = "escolher pasta",
                result = "falha ao persistir permissao",
                error = exception.message
            )
        }
        return true
    }
}
