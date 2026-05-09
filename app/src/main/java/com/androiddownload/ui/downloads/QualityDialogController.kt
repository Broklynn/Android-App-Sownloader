package com.androiddownload.ui.downloads

import android.app.Activity
import com.androiddownload.R
import com.androiddownload.ui.common.DarkDialogFactory
import com.androiddownload.ui.common.DarkOption

data class QualityOptionUi(
    val label: String,
    val preferenceValue: String,
    val formatSelector: String?
)

class QualityDialogController(
    private val activity: Activity
) {
    fun showDefaultQualityDialog(
        options: List<QualityOptionUi>,
        selectedIndex: Int,
        onSelected: (QualityOptionUi) -> Unit
    ) {
        DarkDialogFactory.showOptionsDialog(
            activity = activity,
            title = activity.getString(R.string.default_video_quality_title),
            options = options.map { DarkOption(it.label, sectionForQualityLabel(it.label)) },
            selectedIndex = selectedIndex
        ) { which ->
            onSelected(options[which])
        }
    }

    fun showDownloadQualityDialog(
        options: List<QualityOptionUi>,
        onSelected: (QualityOptionUi) -> Unit
    ) {
        DarkDialogFactory.showOptionsDialog(
            activity = activity,
            title = activity.getString(R.string.choose_quality_title),
            options = options.map { DarkOption(it.label, sectionForQualityLabel(it.label)) }
        ) { which ->
            onSelected(options[which])
        }
    }

    private fun sectionForQualityLabel(label: String): String? {
        return when {
            label.startsWith("MP4", ignoreCase = true) -> activity.getString(R.string.quality_section_video)
            label.startsWith("MP3", ignoreCase = true) -> activity.getString(R.string.quality_section_audio)
            else -> null
        }
    }
}
