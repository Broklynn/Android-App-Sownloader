package com.androiddownload.ui.downloads

import android.app.Activity
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.androiddownload.R

class QuickDownloadSheetRenderer(
    private val activity: Activity
) {
    data class Callbacks(
        val onSelected: (QualityOptionUi) -> Unit,
        val onDownload: () -> Unit,
        val onCancel: () -> Unit
    )

    private var selectedOption: QualityOptionUi? = null
    private lateinit var downloadButton: Button
    private val optionRows = mutableMapOf<QualityOptionUi, TextView>()

    fun build(
        url: String,
        options: List<QualityOptionUi>,
        callbacks: Callbacks
    ): View {
        selectedOption = null
        optionRows.clear()

        val groupedOptions = groupOptions(options)
        downloadButton = Button(activity).apply {
            text = activity.getString(R.string.download)
            isEnabled = false
            isAllCaps = false
            setTextColor(activity.getColor(R.color.button_primary_text))
            setBackgroundResource(R.drawable.bg_button_primary)
            setOnClickListener { callbacks.onDownload() }
        }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_bottom_sheet_surface)
            setPadding(activity.dp(20), activity.dp(10), activity.dp(20), activity.dp(20))

            addView(buildHandle())
            addView(buildHeader(callbacks.onCancel))
            addView(buildUrlPreview(url))
            addView(
                ScrollView(activity).apply {
                    addView(buildOptions(groupedOptions, callbacks))
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    activity.dp(360)
                ).apply {
                    topMargin = activity.dp(14)
                }
            )
            addView(
                downloadButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    activity.dp(52)
                ).apply {
                    topMargin = activity.dp(16)
                }
            )
        }
    }

    private fun buildHandle(): View {
        return View(activity).apply {
            setBackgroundColor(activity.getColor(R.color.line))
            layoutParams = LinearLayout.LayoutParams(activity.dp(48), activity.dp(4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = activity.dp(14)
            }
        }
    }

    private fun buildHeader(onCancel: () -> Unit): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                TextView(activity).apply {
                    text = "Baixar vídeo como"
                    setTextColor(activity.getColor(R.color.text_primary))
                    textSize = 20f
                    typeface = Typeface.DEFAULT_BOLD
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(
                Button(activity).apply {
                    text = activity.getString(R.string.details_close)
                    isAllCaps = false
                    minHeight = 0
                    minWidth = 0
                    setTextColor(activity.getColor(R.color.button_secondary_text))
                    setBackgroundResource(R.drawable.bg_button_secondary)
                    setOnClickListener { onCancel() }
                },
                LinearLayout.LayoutParams(activity.dp(112), activity.dp(42))
            )
        }
    }

    private fun buildUrlPreview(url: String): View {
        return TextView(activity).apply {
            text = url
            setTextColor(activity.getColor(R.color.text_muted))
            textSize = 12f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            setPadding(0, activity.dp(8), 0, 0)
        }
    }

    private fun buildOptions(
        groupedOptions: List<Pair<String, List<QualityOptionUi>>>,
        callbacks: Callbacks
    ): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            groupedOptions.forEachIndexed { groupIndex, (title, groupOptions) ->
                addView(
                    buildSectionTitle(title),
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = if (groupIndex == 0) 0 else activity.dp(14)
                    }
                )
                groupOptions.forEach { option ->
                    addView(buildOptionRow(option, callbacks), optionRowParams())
                }
            }
        }
    }

    private fun buildSectionTitle(title: String): View {
        return TextView(activity).apply {
            text = title
            setTextColor(activity.getColor(R.color.brand))
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        }
    }

    private fun buildOptionRow(option: QualityOptionUi, callbacks: Callbacks): View {
        return TextView(activity).apply {
            text = option.label
            setTextColor(activity.getColor(R.color.text_primary))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(14), 0, activity.dp(14), 0)
            setBackgroundResource(R.drawable.bg_dialog_option)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                select(option)
                callbacks.onSelected(option)
            }
            optionRows[option] = this
        }
    }

    private fun optionRowParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            activity.dp(56)
        ).apply {
            topMargin = activity.dp(8)
        }
    }

    private fun select(option: QualityOptionUi) {
        selectedOption = option
        optionRows.forEach { (rowOption, row) ->
            val selected = rowOption == option
            row.setTextColor(activity.getColor(if (selected) R.color.button_primary_text else R.color.text_primary))
            row.setBackgroundResource(if (selected) R.drawable.bg_button_primary else R.drawable.bg_dialog_option)
        }
        downloadButton.isEnabled = true
    }

    private fun groupOptions(options: List<QualityOptionUi>): List<Pair<String, List<QualityOptionUi>>> {
        val music = options.filter { it.label.contains("MP3", ignoreCase = true) }
        val video = options.filter { it.label.contains("MP4", ignoreCase = true) }
        val other = options.filterNot { option ->
            music.any { it.preferenceValue == option.preferenceValue } ||
                video.any { it.preferenceValue == option.preferenceValue }
        }
        return buildList {
            if (music.isNotEmpty()) add("Música" to music)
            if (video.isNotEmpty()) add("Vídeo" to video)
            if (other.isNotEmpty()) add("Outros" to other)
        }
    }

    private fun Activity.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
