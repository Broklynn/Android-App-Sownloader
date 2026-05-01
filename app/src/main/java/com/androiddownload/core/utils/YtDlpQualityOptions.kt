package com.androiddownload.core.utils

import android.content.Context
import com.androiddownload.core.model.DownloadEntity
import com.androiddownload.core.utils.DownloadSourceClassifier
import com.yausername.youtubedl_android.mapper.VideoInfo

data class YtDlpQualityOption(
    val label: String,
    val formatSelector: String
)

object YtDlpQualityOptions {
    fun build(context: Context, videoInfo: VideoInfo?): List<YtDlpQualityOption> {
        return listOf(
            YtDlpQualityOption(context.getString(com.androiddownload.R.string.ytdlp_quality_mp4_best), "best"),
            YtDlpQualityOption(context.getString(com.androiddownload.R.string.ytdlp_quality_mp4_720p), "best[height<=720]"),
            YtDlpQualityOption(context.getString(com.androiddownload.R.string.ytdlp_quality_mp4_480p), "best[height<=480]"),
            YtDlpQualityOption(context.getString(com.androiddownload.R.string.ytdlp_quality_mp4_360p), "best[height<=360]"),
            YtDlpQualityOption(context.getString(com.androiddownload.R.string.ytdlp_quality_mp3), "mp3")
        )
    }

    fun displayTitle(videoInfo: VideoInfo?): String {
        val title = videoInfo?.fulltitle?.takeIf { it.isNotBlank() }
            ?: videoInfo?.title?.takeIf { it.isNotBlank() }
            ?: "Escolha a qualidade"
        return title
    }

    fun labelForDownload(download: DownloadEntity): String {
        val selector = download.qualitySelector?.trim().orEmpty()
        return when {
            DownloadSourceClassifier.shouldUseHttpDownloader(download.sourceUrl) -> "Download direto"
            selector.isBlank() -> "Formato personalizado"
            selector == "best" -> "MP4 - Melhor qualidade"
            selector == "best[height<=720]" -> "MP4 - 720p"
            selector == "best[height<=480]" -> "MP4 - 480p"
            selector == "best[height<=360]" -> "MP4 - 360p"
            selector == "mp3" -> "MP3"
            selector == "bestaudio" -> "Áudio original"
            else -> "Formato personalizado"
        }
    }
}
