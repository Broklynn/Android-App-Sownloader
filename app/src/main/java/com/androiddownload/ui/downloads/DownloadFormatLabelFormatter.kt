package com.androiddownload.ui.downloads

import android.content.Context
import com.androiddownload.R
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.utils.YtDlpQualityOptions

object DownloadFormatLabelFormatter {
    fun labelForDownload(context: Context, download: DownloadEntity): String {
        val label = YtDlpQualityOptions.labelForDownload(context, download)
        return if (label.isBlank()) context.getString(R.string.download_direct) else label
    }
}
