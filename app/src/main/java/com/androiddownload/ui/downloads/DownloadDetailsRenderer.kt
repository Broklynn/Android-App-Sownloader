package com.androiddownload.ui.downloads

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.androiddownload.R
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.model.DownloadStatus
import com.androiddownload.core.utils.FileSizeFormatter
import java.text.DateFormat
import java.util.Date

class DownloadDetailsRenderer(
    private val context: Context,
    private val callbacks: Callbacks,
    private val statusLabelProvider: (DownloadStatus) -> String,
    private val formatLabelProvider: (DownloadEntity) -> String,
    private val progressLabelProvider: (DownloadEntity) -> String
) {
    data class Callbacks(
        val onShare: (DownloadEntity) -> Unit
    )

    fun buildContent(download: DownloadEntity): View {
        val padding = context.dp(16)
        val textView = TextView(context).apply {
            setTextIsSelectable(true)
            setTextColor(context.getColor(R.color.text_secondary))
            textSize = 13f
            setPadding(0, 0, 0, padding)
            text = buildDetailsText(download)
        }

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(padding, padding, padding, padding)
            addView(
                ScrollView(context).apply {
                    addView(
                        textView,
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    )
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            if (download.status == DownloadStatus.COMPLETED) {
                addView(
                    Button(context).apply {
                        text = context.getString(R.string.details_share)
                        setTextColor(context.getColor(R.color.button_secondary_text))
                        setBackgroundResource(R.drawable.bg_button_secondary)
                        setOnClickListener {
                            callbacks.onShare(download)
                        }
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = padding
                    }
                )
            }
        }
    }

    fun buildDetailsText(download: DownloadEntity): String {
        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
        val details = linkedMapOf(
            context.getString(R.string.download_detail_file_name) to download.fileName,
            context.getString(R.string.download_detail_status) to statusLabelProvider(download.status),
            context.getString(R.string.download_detail_format) to formatLabelProvider(download),
            context.getString(R.string.download_detail_source_url) to download.sourceUrl,
            context.getString(R.string.download_detail_downloaded) to buildDownloadSizeText(download),
            context.getString(R.string.download_detail_progress) to progressLabelProvider(download),
            context.getString(R.string.download_detail_speed) to formatSpeedForDetails(download.speed),
            context.getString(R.string.download_detail_error) to (
                download.errorMessage?.takeIf { it.isNotBlank() } ?: context.getString(R.string.none)
                ),
            context.getString(R.string.download_detail_final_uri) to (
                download.destinationUri?.takeIf { it.isNotBlank() } ?: context.getString(R.string.not_available)
                ),
            context.getString(R.string.download_detail_created_at) to dateFormat.format(Date(download.createdAt)),
            context.getString(R.string.download_detail_updated_at) to dateFormat.format(Date(download.updatedAt))
        )

        return details.entries.joinToString(separator = "\n\n") { (label, value) ->
            "$label\n$value"
        }
    }

    private fun buildDownloadSizeText(download: DownloadEntity): String {
        val downloaded = FileSizeFormatter.formatBytes(download.downloadedBytes)
        val total = FileSizeFormatter.formatBytes(download.totalBytes)
        return "$downloaded / $total"
    }

    private fun formatSpeedForDetails(speed: Long): String {
        return if (speed > 0L) {
            FileSizeFormatter.formatSpeed(speed)
        } else {
            context.getString(R.string.not_available)
        }
    }

    private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
