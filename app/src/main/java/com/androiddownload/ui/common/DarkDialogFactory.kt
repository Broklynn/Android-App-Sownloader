package com.androiddownload.ui.common

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.androiddownload.R

data class DarkDialogButton(
    val label: String,
    val primary: Boolean = false,
    val onClick: (() -> Unit)? = null
)

data class DarkOption(
    val label: String,
    val section: String? = null
)

object DarkDialogFactory {
    fun showMessageDialog(
        activity: Activity,
        title: String,
        message: String,
        buttons: List<DarkDialogButton>
    ): AlertDialog {
        val messageView = TextView(activity).apply {
            text = message
            setTextColor(activity.getColor(R.color.text_secondary))
            textSize = 14f
            setLineSpacing(activity.dp(2).toFloat(), 1f)
        }
        return showContentDialog(activity, title, messageView, buttons)
    }

    fun showOptionsDialog(
        activity: Activity,
        title: String,
        options: List<DarkOption>,
        selectedIndex: Int = -1,
        neutralButton: DarkDialogButton? = null,
        onSelected: (Int) -> Unit
    ): AlertDialog {
        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        var lastSection: String? = null
        lateinit var dialog: AlertDialog
        options.forEachIndexed { index, option ->
            if (!option.section.isNullOrBlank() && option.section != lastSection) {
                lastSection = option.section
                list.addView(
                    TextView(activity).apply {
                        text = option.section
                        setTextColor(activity.getColor(R.color.brand))
                        textSize = 12f
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        includeFontPadding = false
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = if (list.childCount == 0) 0 else activity.dp(12)
                        bottomMargin = activity.dp(8)
                    }
                )
            }
            val selected = index == selectedIndex
            list.addView(
                TextView(activity).apply {
                    text = option.label
                    setTextColor(activity.getColor(if (selected) R.color.brand else R.color.text_primary))
                    textSize = 14f
                    typeface = if (selected) {
                        android.graphics.Typeface.DEFAULT_BOLD
                    } else {
                        android.graphics.Typeface.DEFAULT
                    }
                    setBackgroundResource(if (selected) R.drawable.bg_button_secondary else R.drawable.bg_dialog_option)
                    setPadding(activity.dp(14), activity.dp(12), activity.dp(14), activity.dp(12))
                    isClickable = true
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    maxLines = 3
                    setOnClickListener {
                        dialog.dismiss()
                        onSelected(index)
                    }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = activity.dp(8)
                }
            )
        }

        val buttons = buildList {
            neutralButton?.let { add(it) }
            add(DarkDialogButton(activity.getString(R.string.details_close)))
        }
        dialog = showContentDialog(
            activity = activity,
            title = title,
            contentView = ScrollView(activity).apply { addView(list) },
            buttons = buttons
        )
        return dialog
    }

    fun showContentDialog(
        activity: Activity,
        title: String,
        contentView: View,
        buttons: List<DarkDialogButton>
    ): AlertDialog {
        lateinit var dialog: AlertDialog
        val padding = activity.dp(20)
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_dialog_surface)
            setPadding(padding, padding, padding, padding)
            addView(
                TextView(context).apply {
                    text = title
                    setTextColor(activity.getColor(R.color.text_primary))
                    textSize = 20f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = activity.dp(14)
                }
            )
            addView(
                contentView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                LinearLayout(context).apply {
                    val stackButtons = buttons.size > 2
                    orientation = if (stackButtons) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.END
                    buttons.forEachIndexed { index, buttonSpec ->
                        addView(
                            Button(context).apply {
                                text = buttonSpec.label
                                isAllCaps = false
                                minHeight = 0
                                minWidth = 0
                                setTextColor(
                                    activity.getColor(
                                        if (buttonSpec.primary) {
                                            R.color.button_primary_text
                                        } else {
                                            R.color.button_secondary_text
                                        }
                                    )
                                )
                                setBackgroundResource(
                                    if (buttonSpec.primary) {
                                        R.drawable.bg_button_primary
                                    } else {
                                        R.drawable.bg_button_secondary
                                    }
                                )
                                setPadding(activity.dp(14), 0, activity.dp(14), 0)
                                setOnClickListener {
                                    dialog.dismiss()
                                    buttonSpec.onClick?.invoke()
                                }
                            },
                            if (stackButtons) {
                                LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                    activity.dp(42)
                                ).apply {
                                    if (index > 0) topMargin = activity.dp(8)
                                }
                            } else {
                                LinearLayout.LayoutParams(
                                    0,
                                    activity.dp(42),
                                    1f
                                ).apply {
                                    if (index > 0) leftMargin = activity.dp(8)
                                }
                            }
                        )
                    }
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = activity.dp(18)
                }
            )
        }

        dialog = AlertDialog.Builder(activity)
            .setView(container)
            .create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        dialog.show()
        return dialog
    }

    private fun Activity.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
