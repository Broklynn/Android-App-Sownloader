package com.androiddownload.ui.player

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.androiddownload.R
import com.androiddownload.core.model.DownloadEntity

class PlayerListRenderer(
    private val context: Context,
    private val playerList: LinearLayout,
    private val playerEmptyText: TextView,
    private val formatLabelProvider: (DownloadEntity) -> String,
    private val onItemClick: (Int) -> Unit
) {
    fun render(
        items: List<DownloadEntity>,
        category: PlayerCategory,
        currentIndex: Int
    ) {
        playerList.removeAllViews()
        playerEmptyText.text = if (category == PlayerCategory.MUSIC) {
            context.getString(R.string.player_empty_music)
        } else {
            context.getString(R.string.player_empty_video)
        }
        playerEmptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE

        items.forEachIndexed { index, download ->
            val card = buildPlayerDownloadCard(download, index, category, currentIndex)
            playerList.addView(
                card,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) topMargin = dp(10)
                }
            )
        }
    }

    private fun buildPlayerDownloadCard(
        download: DownloadEntity,
        index: Int,
        category: PlayerCategory,
        currentIndex: Int
    ): View {
        val isCurrent = index == currentIndex
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_download_item)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setOnClickListener { onItemClick(index) }

            addView(
                TextView(context).apply {
                    text = download.fileName
                    setTextColor(context.getColor(if (isCurrent) R.color.brand else R.color.text_primary))
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

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            row.addView(
                TextView(context).apply {
                    text = formatLabelProvider(download)
                    setTextColor(context.getColor(R.color.text_secondary))
                    textSize = 13f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )

            row.addView(
                Button(context).apply {
                    text = if (category == PlayerCategory.MUSIC) {
                        context.getString(R.string.player_play)
                    } else {
                        context.getString(R.string.player_watch)
                    }
                    isAllCaps = false
                    minHeight = 0
                    minimumHeight = 0
                    setTextColor(context.getColor(R.color.button_secondary_text))
                    setBackgroundResource(R.drawable.bg_button_secondary)
                    textSize = 13f
                    setPadding(dp(14), 0, dp(14), 0)
                    setOnClickListener { onItemClick(index) }
                },
                LinearLayout.LayoutParams(dp(104), dp(40)).apply {
                    marginStart = dp(12)
                }
            )

            addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(10)
                }
            )
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
