package com.androiddownload.core.files

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.androiddownload.R
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import java.io.File
import java.util.Locale

class FileActionsController(
    private val activity: Activity,
    private val onMessage: (String) -> Unit
) {
    fun open(download: DownloadEntity) {
        if (download.status != DownloadStatus.COMPLETED) return

        val openUri = try {
            resolveOpenUri(download)
        } catch (exception: IllegalArgumentException) {
            null
        }

        if (openUri == null) {
            onMessage(activity.getString(R.string.download_file_not_found))
            return
        }

        val mimeType = normalizeMimeType(download.mimeType)
            ?: activity.contentResolver.getType(openUri)
            ?: inferMimeType(download.fileName)
            ?: "*/*"

        val viewIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(openUri, mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        if (activity.packageManager.queryIntentActivities(
                viewIntent,
                PackageManager.MATCH_DEFAULT_ONLY
            ).isEmpty()
        ) {
            onMessage(activity.getString(R.string.download_no_app_found))
            return
        }

        try {
            activity.startActivity(Intent.createChooser(viewIntent, activity.getString(R.string.open_file_chooser)))
        } catch (exception: ActivityNotFoundException) {
            onMessage(activity.getString(R.string.download_no_app_found))
        } catch (exception: RuntimeException) {
            onMessage(activity.getString(R.string.download_open_error))
        }
    }

    fun share(download: DownloadEntity) {
        if (download.status != DownloadStatus.COMPLETED) return

        val shareUri = try {
            resolveOpenUri(download)
        } catch (exception: IllegalArgumentException) {
            null
        }

        if (shareUri == null) {
            onMessage(activity.getString(R.string.share_file_not_found))
            return
        }

        val mimeType = normalizeMimeType(download.mimeType)
            ?: activity.contentResolver.getType(shareUri)
            ?: inferMimeType(download.fileName)
            ?: "application/octet-stream"

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, shareUri)
            clipData = ClipData.newUri(activity.contentResolver, download.fileName, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        if (activity.packageManager.queryIntentActivities(
                sendIntent,
                PackageManager.MATCH_DEFAULT_ONLY
            ).isEmpty()
        ) {
            onMessage(activity.getString(R.string.share_no_app_found))
            return
        }

        try {
            activity.startActivity(Intent.createChooser(sendIntent, activity.getString(R.string.share_file_chooser)))
        } catch (exception: ActivityNotFoundException) {
            onMessage(activity.getString(R.string.share_no_app_found))
        } catch (exception: RuntimeException) {
            onMessage(activity.getString(R.string.share_open_error))
        }
    }

    private fun resolveOpenUri(download: DownloadEntity): Uri? {
        val destination = download.destinationUri?.takeIf { it.isNotBlank() } ?: return null
        val destinationUri = Uri.parse(destination)

        return when (destinationUri.scheme) {
            "content" -> destinationUri
            "file" -> {
                val file = File(destinationUri.path ?: return null)
                if (!file.exists()) return null
                FileProvider.getUriForFile(activity, fileProviderAuthority(), file)
            }
            null -> {
                val file = File(destination)
                if (!file.exists()) return null
                FileProvider.getUriForFile(activity, fileProviderAuthority(), file)
            }
            else -> null
        }
    }

    private fun fileProviderAuthority(): String = "${activity.packageName}.fileprovider"

    private fun normalizeMimeType(mimeType: String?): String? {
        return mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.takeIf { it.contains('/') }
    }

    private fun inferMimeType(fileName: String): String? {
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.US)
            .takeIf { it.isNotBlank() }
            ?: return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    }
}
