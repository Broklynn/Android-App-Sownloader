package com.androiddownload.ui.downloads

import android.app.Activity
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.androiddownload.R
import com.androiddownload.download.media.SharedMediaItem
import com.androiddownload.download.media.SharedMediaPreview
import com.androiddownload.download.media.SharedMediaType

class MediaSelectionSheetRenderer(
    private val activity: Activity
) {
    data class Callbacks(
        val onDownloadSelected: (List<SharedMediaItem>) -> Unit,
        val onDownloadAll: (List<SharedMediaItem>) -> Unit,
        val onCancel: () -> Unit
    )

    private var items: List<SharedMediaItem> = emptyList()
    private val selectedIndexes = linkedSetOf<Int>()
    private val itemRows = mutableMapOf<Int, LinearLayout>()
    private lateinit var selectedButton: Button

    fun build(
        preview: SharedMediaPreview,
        callbacks: Callbacks
    ): View {
        items = preview.items
        selectedIndexes.clear()
        itemRows.clear()

        selectedButton = Button(activity).apply {
            text = "Baixar selecionados"
            isEnabled = false
            isAllCaps = false
            setTextColor(activity.getColor(R.color.button_primary_text))
            setBackgroundResource(R.drawable.bg_button_primary)
            setOnClickListener { callbacks.onDownloadSelected(selectedItems()) }
        }

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_bottom_sheet_surface)
            setPadding(activity.dp(20), activity.dp(10), activity.dp(20), activity.dp(20))

            addView(buildHandle())
            addView(buildHeader(callbacks.onCancel))
            addView(buildSubtitle(preview))

            if (items.isEmpty()) {
                addView(buildEmptyState(), emptyStateParams())
            } else {
                addView(buildItemList(), listParams())
                addView(buildActions(callbacks), actionsParams())
            }
        }
    }

    fun selectedItems(): List<SharedMediaItem> {
        return items.filter { item -> selectedIndexes.contains(item.index) }
    }

    fun updateSelection(item: SharedMediaItem, selected: Boolean) {
        if (selected) {
            selectedIndexes += item.index
        } else {
            selectedIndexes -= item.index
        }
        itemRows[item.index]?.let { updateRowState(it, selected) }
        selectedButton.isEnabled = selectedIndexes.isNotEmpty()
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
                    text = "Escolha o que baixar"
                    setTextColor(activity.getColor(R.color.text_primary))
                    textSize = 20f
                    typeface = Typeface.DEFAULT_BOLD
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
            addView(
                Button(activity).apply {
                    text = "Cancelar"
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

    private fun buildSubtitle(preview: SharedMediaPreview): View {
        val label = preview.title?.takeIf { it.isNotBlank() }
            ?: "Selecione um ou mais itens deste post"
        return TextView(activity).apply {
            text = label
            setTextColor(activity.getColor(R.color.text_muted))
            textSize = 13f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, activity.dp(8), 0, 0)
        }
    }

    private fun buildEmptyState(): View {
        return TextView(activity).apply {
            text = "Nenhuma midia encontrada neste post"
            setTextColor(activity.getColor(R.color.text_secondary))
            textSize = 15f
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_dialog_option)
            setPadding(activity.dp(16), activity.dp(24), activity.dp(16), activity.dp(24))
        }
    }

    private fun buildItemList(): View {
        return ScrollView(activity).apply {
            addView(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    items.forEach { item ->
                        addView(buildItemRow(item), itemRowParams())
                    }
                }
            )
        }
    }

    private fun buildItemRow(item: SharedMediaItem): LinearLayout {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(14), 0, activity.dp(14), 0)
            setBackgroundResource(R.drawable.bg_dialog_option)
            isClickable = true
            isFocusable = true

            addView(
                TextView(activity).apply {
                    text = "${item.type.displayLabel()} ${item.index}"
                    setTextColor(activity.getColor(R.color.brand))
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    includeFontPadding = false
                }
            )
            addView(
                TextView(activity).apply {
                    text = item.title
                    setTextColor(activity.getColor(R.color.text_primary))
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, activity.dp(4), 0, 0)
                }
            )

            setOnClickListener {
                updateSelection(item, selected = !selectedIndexes.contains(item.index))
            }
            itemRows[item.index] = this
        }
    }

    private fun buildActions(callbacks: Callbacks): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                selectedButton,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    activity.dp(52)
                )
            )
            addView(
                Button(activity).apply {
                    text = "Baixar todos"
                    isAllCaps = false
                    setTextColor(activity.getColor(R.color.button_secondary_text))
                    setBackgroundResource(R.drawable.bg_button_secondary)
                    setOnClickListener { callbacks.onDownloadAll(items) }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    activity.dp(52)
                ).apply {
                    topMargin = activity.dp(10)
                }
            )
        }
    }

    private fun updateRowState(row: LinearLayout, selected: Boolean) {
        row.setBackgroundResource(if (selected) R.drawable.bg_button_primary else R.drawable.bg_dialog_option)
        updateTextColors(row, selected)
    }

    private fun updateTextColors(parent: ViewGroup, selected: Boolean) {
        val primaryColor = activity.getColor(if (selected) R.color.button_primary_text else R.color.text_primary)
        val labelColor = activity.getColor(if (selected) R.color.button_primary_text else R.color.brand)
        for (index in 0 until parent.childCount) {
            when (val child = parent.getChildAt(index)) {
                is TextView -> child.setTextColor(if (index == 0) labelColor else primaryColor)
                is ViewGroup -> updateTextColors(child, selected)
            }
        }
    }

    private fun emptyStateParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = activity.dp(18)
        }
    }

    private fun listParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            activity.dp(360)
        ).apply {
            topMargin = activity.dp(14)
        }
    }

    private fun actionsParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = activity.dp(16)
        }
    }

    private fun itemRowParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            activity.dp(68)
        ).apply {
            topMargin = activity.dp(8)
        }
    }

    private fun SharedMediaType.displayLabel(): String {
        return when (this) {
            SharedMediaType.VIDEO -> "Video"
            SharedMediaType.AUDIO -> "Audio"
            SharedMediaType.IMAGE -> "Imagem"
            SharedMediaType.UNKNOWN -> "Desconhecido"
        }
    }

    private fun Activity.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
