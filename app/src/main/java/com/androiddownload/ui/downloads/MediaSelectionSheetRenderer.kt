package com.androiddownload.ui.downloads

import android.app.Activity
import android.graphics.Matrix
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.androiddownload.R
import com.androiddownload.download.media.SharedMediaItem
import com.androiddownload.download.media.SharedMediaPreview
import com.androiddownload.download.media.SharedMediaType
import kotlin.math.max

class MediaSelectionSheetRenderer(
    private val activity: Activity
) {
    data class Callbacks(
        val onDownloadSelected: (List<SharedMediaItem>) -> Unit,
        val onDownloadAll: (List<SharedMediaItem>) -> Unit,
        val onCancel: () -> Unit
    )

    private data class ItemRowViews(
        val container: LinearLayout,
        val title: TextView,
        val check: TextView
    )

    private var items: List<SharedMediaItem> = emptyList()
    private val selectedIndexes = linkedSetOf<Int>()
    private val itemRows = mutableMapOf<Int, ItemRowViews>()
    private val thumbnailLoader = MediaThumbnailLoader()
    private lateinit var selectedButton: Button

    fun build(
        preview: SharedMediaPreview,
        callbacks: Callbacks
    ): View {
        items = preview.items
        selectedIndexes.clear()
        itemRows.clear()

        selectedButton = buildSelectedButton(callbacks)

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_bottom_sheet_surface)
            setPadding(activity.dp(20), activity.dp(10), activity.dp(20), activity.dp(14))
            addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = Unit

                override fun onViewDetachedFromWindow(view: View) {
                    thumbnailLoader.cancel()
                }
            })

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

    private fun buildSelectedButton(callbacks: Callbacks): Button {
        return Button(activity).apply {
            text = "Baixar selecionados"
            isEnabled = false
            isAllCaps = false
            setTextColor(activity.getColor(R.color.button_primary_text))
            setBackgroundResource(R.drawable.bg_button_primary)
            setOnClickListener { callbacks.onDownloadSelected(selectedItems()) }
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
        val countLabel = when (items.size) {
            0 -> "Nenhuma m\u00eddia encontrada"
            1 -> "1 m\u00eddia encontrada"
            else -> "${items.size} m\u00eddias encontradas"
        }
        val hint = preview.title?.takeIf { it.isNotBlank() }
            ?: "Selecione uma ou mais m\u00eddias deste post"
        return TextView(activity).apply {
            text = "$countLabel - $hint"
            setTextColor(activity.getColor(R.color.text_muted))
            textSize = 13f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, activity.dp(8), 0, 0)
        }
    }

    private fun buildEmptyState(): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_media_selection_item)
            setPadding(activity.dp(18), activity.dp(28), activity.dp(18), activity.dp(28))
            addView(
                TextView(activity).apply {
                    text = "Nenhuma m\u00eddia encontrada neste post"
                    setTextColor(activity.getColor(R.color.text_primary))
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                }
            )
            addView(
                TextView(activity).apply {
                    text = "Tente outro compartilhamento ou feche esta janela."
                    setTextColor(activity.getColor(R.color.text_muted))
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setPadding(0, activity.dp(8), 0, 0)
                }
            )
        }
    }

    private fun buildItemList(): View {
        return ScrollView(activity).apply {
            isFillViewport = false
            addView(
                GridLayout(activity).apply {
                    columnCount = GRID_COLUMNS
                    items.forEachIndexed { position, item ->
                        addView(buildItemCard(item, position + 1), gridItemParams())
                    }
                }
            )
        }
    }

    private fun buildItemCard(item: SharedMediaItem, displayIndex: Int): LinearLayout {
        lateinit var titleView: TextView
        val checkView = buildSelectionIndicator()

        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(10))
            setBackgroundResource(R.drawable.bg_media_selection_item)
            isClickable = true
            isFocusable = true

            addView(buildThumbnail(item, checkView), thumbnailParams())
            addView(buildItemTitle(item, displayIndex) { title ->
                titleView = title
            }, textBlockParams())

            setOnClickListener {
                updateSelection(item, selected = !selectedIndexes.contains(item.index))
            }
            itemRows[item.index] = ItemRowViews(
                container = this,
                title = titleView,
                check = checkView
            )
        }
    }

    private fun buildItemTitle(
        item: SharedMediaItem,
        displayIndex: Int,
        capture: (TextView) -> Unit
    ): View {
        lateinit var titleView: TextView
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, activity.dp(12), 0, 0)
            titleView = TextView(activity).apply {
                text = item.displayTitle(displayIndex)
                setTextColor(activity.getColor(R.color.text_primary))
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                includeFontPadding = false
            }
            addView(titleView)
            capture(titleView)
        }
    }

    private fun buildThumbnail(item: SharedMediaItem, checkView: TextView): View {
        val imageView = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.MATRIX
            setBackgroundResource(R.drawable.bg_media_selection_thumbnail)
            visibility = View.INVISIBLE
        }
        val placeholder = TextView(activity).apply {
            text = item.type.placeholderText()
            gravity = Gravity.CENTER
            setTextColor(activity.getColor(R.color.text_primary))
            textSize = if (item.type == SharedMediaType.IMAGE) 12f else 24f
            typeface = Typeface.DEFAULT_BOLD
        }

        return FrameLayout(activity).apply {
            setBackgroundResource(R.drawable.bg_media_selection_thumbnail)
            clipToOutline = true
            addView(placeholder, matchParentFrameParams())
            addView(imageView, matchParentFrameParams())
            addView(checkView, checkOverlayParams())
            thumbnailLoader.load(item.thumbnailUrl, imageView) {
                imageView.post { applyThumbnailCrop(imageView) }
                imageView.visibility = View.VISIBLE
            }
        }
    }

    private fun applyThumbnailCrop(imageView: ImageView) {
        val drawable = imageView.drawable ?: return
        val drawableWidth = drawable.intrinsicWidth.takeIf { it > 0 } ?: return
        val drawableHeight = drawable.intrinsicHeight.takeIf { it > 0 } ?: return
        val viewWidth = imageView.width.takeIf { it > 0 } ?: return
        val viewHeight = imageView.height.takeIf { it > 0 } ?: return

        val scale = max(
            viewWidth.toFloat() / drawableWidth.toFloat(),
            viewHeight.toFloat() / drawableHeight.toFloat()
        ) * THUMBNAIL_ZOOM
        val translateX = (viewWidth - drawableWidth * scale) * 0.5f
        val translateY = (viewHeight - drawableHeight * scale) * 0.5f

        imageView.imageMatrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(translateX, translateY)
        }
    }

    private fun buildSelectionIndicator(): TextView {
        return TextView(activity).apply {
            text = ""
            gravity = Gravity.CENTER
            includeFontPadding = false
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(activity.getColor(R.color.surface))
            setBackgroundResource(R.drawable.bg_media_selection_check)
        }
    }

    private fun buildActions(callbacks: Callbacks): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_media_selection_footer)
            setPadding(activity.dp(10), activity.dp(10), activity.dp(10), activity.dp(10))
            addView(selectedButton, fullWidthButtonParams(52))
            addView(
                Button(activity).apply {
                    text = "Baixar todos"
                    isAllCaps = false
                    setTextColor(activity.getColor(R.color.button_secondary_text))
                    setBackgroundResource(R.drawable.bg_button_secondary)
                    setOnClickListener { callbacks.onDownloadAll(items) }
                },
                fullWidthButtonParams(52, topMargin = 10)
            )
        }
    }

    private fun updateRowState(row: ItemRowViews, selected: Boolean) {
        row.container.setBackgroundResource(
            if (selected) R.drawable.bg_media_selection_item_selected else R.drawable.bg_media_selection_item
        )
        row.title.setTextColor(activity.getColor(R.color.text_primary))
        row.check.apply {
            text = if (selected) "\u2713" else ""
            setBackgroundResource(
                if (selected) R.drawable.bg_media_selection_check_selected else R.drawable.bg_media_selection_check
            )
            setTextColor(activity.getColor(if (selected) R.color.surface else R.color.text_muted))
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
            topMargin = activity.dp(14)
        }
    }

    private fun gridItemParams(): GridLayout.LayoutParams {
        return GridLayout.LayoutParams().apply {
            width = 0
            height = activity.dp(178)
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(activity.dp(4), activity.dp(4), activity.dp(4), activity.dp(8))
        }
    }

    private fun thumbnailParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            activity.dp(124)
        )
    }

    private fun textBlockParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }

    private fun checkOverlayParams(): FrameLayout.LayoutParams {
        return FrameLayout.LayoutParams(activity.dp(30), activity.dp(30)).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = activity.dp(8)
            rightMargin = activity.dp(8)
        }
    }

    private fun fullWidthButtonParams(
        height: Int,
        topMargin: Int = 0
    ): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            activity.dp(height)
        ).apply {
            this.topMargin = activity.dp(topMargin)
        }
    }

    private fun matchParentFrameParams(): FrameLayout.LayoutParams {
        return FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    }

    private fun SharedMediaItem.displayTitle(displayIndex: Int): String {
        return "${type.displayLabel()} $displayIndex"
    }

    private fun SharedMediaType.displayLabel(): String {
        return when (this) {
            SharedMediaType.VIDEO -> "V\u00eddeo"
            SharedMediaType.AUDIO -> "\u00c1udio"
            SharedMediaType.IMAGE -> "Imagem"
            SharedMediaType.UNKNOWN -> "M\u00eddia"
        }
    }

    private fun SharedMediaType.placeholderText(): String {
        return when (this) {
            SharedMediaType.VIDEO -> "\u25b6"
            SharedMediaType.AUDIO -> "\u266a"
            SharedMediaType.IMAGE -> "IMG"
            SharedMediaType.UNKNOWN -> "?"
        }
    }

    private fun Activity.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val GRID_COLUMNS = 2
        const val THUMBNAIL_ZOOM = 1.55f
    }
}
