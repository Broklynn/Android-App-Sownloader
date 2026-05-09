package com.androiddownload.ui.home

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.androiddownload.R
import com.androiddownload.core.model.DownloadEntity

class HomeRecentDownloadsRenderer(
    private val context: Context,
    private val section: View,
    private val list: LinearLayout,
    private val formatLabelProvider: (DownloadEntity) -> String,
    private val statusLabelProvider: (DownloadEntity) -> String,
    private val sizeTextProvider: (DownloadEntity) -> String,
    private val badgeLabelProvider: (DownloadEntity, String) -> String,
    private val onItemClick: (DownloadEntity) -> Unit
) {
    fun render(downloads: List<DownloadEntity>) {
        section.visibility = View.VISIBLE
        list.removeAllViews()

        if (downloads.isEmpty()) {
            list.addView(
                buildEmptyText(),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            return
        }

        downloads.forEachIndexed { index, download ->
            list.addView(
                buildCard(download),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) topMargin = dp(10)
                }
            )
        }
    }

    private fun buildEmptyText(): View {
        return TextView(context).apply {
            text = context.getString(R.string.home_recent_downloads_empty)
            setTextColor(context.getColor(R.color.text_secondary))
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setBackgroundResource(R.drawable.bg_empty_state)
            setPadding(dp(16), dp(18), dp(16), dp(18))
        }
    }

    private fun buildCard(download: DownloadEntity): View {
        val formatLabel = formatLabelProvider(download)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_download_item)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            isClickable = true
            setOnClickListener { onItemClick(download) }

            addView(
                TextView(context).apply {
                    text = badgeLabelProvider(download, formatLabel)
                    gravity = android.view.Gravity.CENTER
                    setBackgroundResource(R.drawable.bg_media_art_placeholder)
                    setTextColor(context.getColor(R.color.background_main))
                    textSize = 12f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                },
                LinearLayout.LayoutParams(dp(56), dp(56))
            )

            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), 0, 0, 0)

                    addView(
                        TextView(context).apply {
                            text = download.fileName
                            setTextColor(context.getColor(R.color.text_primary))
                            textSize = 15f
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                            maxLines = 1
                            ellipsize = android.text.TextUtils.TruncateAt.END
                        },
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    )

                    addView(
                        TextView(context).apply {
                            text = "$formatLabel - ${statusLabelProvider(download)}"
                            setTextColor(context.getColor(R.color.text_secondary))
                            textSize = 12f
                            maxLines = 1
                            ellipsize = android.text.TextUtils.TruncateAt.END
                        },
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = dp(5)
                        }
                    )

                    addView(
                        TextView(context).apply {
                            text = sizeTextProvider(download)
                            setTextColor(context.getColor(R.color.text_muted))
                            textSize = 12f
                            maxLines = 1
                            ellipsize = android.text.TextUtils.TruncateAt.END
                        },
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = dp(5)
                        }
                    )
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
