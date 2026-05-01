package com.androiddownload.core.utils

import android.content.Context
import com.yausername.youtubedl_android.mapper.VideoInfo

data class YtDlpQualityOption(
    val label: String,
    val formatSelector: String
)

object YtDlpQualityOptions {
    fun build(context: Context, videoInfo: VideoInfo?): List<YtDlpQualityOption> {
        val options = mutableListOf(
            YtDlpQualityOption(context.getString(com.androiddownload.R.string.ytdlp_quality_best), "best"),
            YtDlpQualityOption(context.getString(com.androiddownload.R.string.ytdlp_quality_720p), "best[height<=720]"),
            YtDlpQualityOption(context.getString(com.androiddownload.R.string.ytdlp_quality_480p), "best[height<=480]"),
            YtDlpQualityOption(context.getString(com.androiddownload.R.string.ytdlp_quality_360p), "best[height<=360]")
        )

        options.add(YtDlpQualityOption(context.getString(com.androiddownload.R.string.ytdlp_quality_audio_original), "bestaudio"))
        options.add(YtDlpQualityOption(context.getString(com.androiddownload.R.string.ytdlp_quality_mp3), "mp3"))

        return options
    }

    fun displayTitle(videoInfo: VideoInfo?): String {
        val title = videoInfo?.fulltitle?.takeIf { it.isNotBlank() }
            ?: videoInfo?.title?.takeIf { it.isNotBlank() }
            ?: "Escolha a qualidade"
        return title
    }
}
