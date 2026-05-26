package com.androiddownload.ui.home

import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import com.androiddownload.R
import com.androiddownload.core.preferences.RecentDownloadsStore

class HomeRecentUrlController(
    private val store: RecentDownloadsStore,
    private val section: View,
    private val list: ViewGroup,
    private val clearButton: View,
    private val homeController: HomeController
) {
    init {
        clearButton.setOnClickListener {
            store.clear()
            render()
        }
    }

    fun render() {
        val recentUrls = store.load()
        section.visibility = if (recentUrls.isEmpty()) View.GONE else View.VISIBLE
        clearButton.visibility = if (recentUrls.isEmpty()) View.GONE else View.VISIBLE
        list.removeAllViews()

        recentUrls.take(RecentDownloadsStore.MAX_RECENT_DOWNLOAD_URLS_DISPLAYED).forEachIndexed { index, url ->
            val button = Button(list.context).apply {
                text = url
                setBackgroundResource(R.drawable.bg_chip)
                setTextColor(context.getColor(R.color.button_secondary_text))
                textSize = 12f
                isAllCaps = false
                minHeight = 0
                minimumHeight = 0
                setPadding(
                    dp(12),
                    dp(10),
                    dp(12),
                    dp(10)
                )
                maxLines = 1
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setOnClickListener {
                    homeController.setUrl(url)
                }
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            if (index > 0) {
                params.topMargin = dp(8)
            }
            list.addView(button, params)
        }
    }

    fun addUrl(url: String) {
        store.add(url)
    }

    private fun dp(value: Int): Int = (value * list.resources.displayMetrics.density).toInt()
}
