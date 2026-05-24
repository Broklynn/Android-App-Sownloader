package com.androiddownload.ui.settings

import android.app.Activity
import android.widget.TextView
import com.androiddownload.R
import com.androiddownload.core.preferences.DefaultQualityPreferences
import com.androiddownload.core.utils.YtDlpQualityOptions
import com.androiddownload.ui.downloads.QualityDialogController
import com.androiddownload.ui.downloads.QualityOptionUi

class DefaultQualityController(
    private val activity: Activity,
    private val preferences: DefaultQualityPreferences,
    private val qualityDialogController: QualityDialogController,
    private val valueText: TextView
) {
    fun showDialog() {
        val options = defaultQualityOptions()
        val currentIndex = options.indexOfFirst {
            it.preferenceValue == selectedOption().preferenceValue
        }.coerceAtLeast(0)
        qualityDialogController.showDefaultQualityDialog(options, currentIndex) { option ->
            saveOption(option)
            updateText()
        }
    }

    fun updateText() {
        valueText.text = activity.getString(
            R.string.default_quality_selected,
            selectedOption().label
        )
    }

    fun selectedOption(): QualityOptionUi {
        val savedValue = preferences.load()
        return defaultQualityOptions().firstOrNull { it.preferenceValue == savedValue }
            ?: defaultQualityOptions().first()
    }

    fun downloadQualityOptions(): List<QualityOptionUi> {
        return YtDlpQualityOptions.build(activity, null).map { option ->
            QualityOptionUi(
                label = option.label,
                preferenceValue = option.formatSelector,
                formatSelector = option.formatSelector
            )
        }
    }

    private fun saveOption(option: QualityOptionUi) {
        preferences.save(option.preferenceValue)
    }

    private fun defaultQualityOptions(): List<QualityOptionUi> {
        return listOf(
            QualityOptionUi(
                label = activity.getString(R.string.default_quality_ask_always),
                preferenceValue = DefaultQualityPreferences.DEFAULT_QUALITY_ASK_VALUE,
                formatSelector = null
            )
        ) + downloadQualityOptions()
    }
}
