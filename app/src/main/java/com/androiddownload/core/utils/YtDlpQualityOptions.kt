package com.androiddownload.core.utils

import android.content.Context
import com.androiddownload.R
import com.androiddownload.core.model.DownloadEntity
import com.yausername.youtubedl_android.mapper.VideoInfo

data class YtDlpQualityOption(
    val label: String,
    val formatSelector: String
)

object YtDlpQualityOptions {
    const val SELECTOR_MP4_1440P = "bestvideo[height<=1440][ext=mp4]+bestaudio[ext=m4a]/best[height<=1440][ext=mp4]/best[height<=1440]"
    const val SELECTOR_MP4_1080P = "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best[height<=1080][ext=mp4]/best[height<=1080]"
    const val SELECTOR_MP4_720P = "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720][ext=mp4]/best[height<=720]"
    const val SELECTOR_MP4_480P = "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/best[height<=480][ext=mp4]/best[height<=480]"
    const val SELECTOR_MP3_320K = "mp3:320K"
    const val SELECTOR_MP3_256K = "mp3:256K"
    const val SELECTOR_MP3_192K = "mp3:192K"
    const val SELECTOR_MP3_128K = "mp3:128K"

    fun build(context: Context, videoInfo: VideoInfo?): List<YtDlpQualityOption> {
        return listOf(
            YtDlpQualityOption(context.getString(R.string.ytdlp_quality_mp4_1440p), SELECTOR_MP4_1440P),
            YtDlpQualityOption(context.getString(R.string.ytdlp_quality_mp4_1080p), SELECTOR_MP4_1080P),
            YtDlpQualityOption(context.getString(R.string.ytdlp_quality_mp4_720p), SELECTOR_MP4_720P),
            YtDlpQualityOption(context.getString(R.string.ytdlp_quality_mp4_480p), SELECTOR_MP4_480P),
            YtDlpQualityOption(context.getString(R.string.ytdlp_quality_mp3_320k), SELECTOR_MP3_320K),
            YtDlpQualityOption(context.getString(R.string.ytdlp_quality_mp3_256k), SELECTOR_MP3_256K),
            YtDlpQualityOption(context.getString(R.string.ytdlp_quality_mp3_192k), SELECTOR_MP3_192K),
            YtDlpQualityOption(context.getString(R.string.ytdlp_quality_mp3_128k), SELECTOR_MP3_128K)
        )
    }

    fun displayTitle(context: Context, videoInfo: VideoInfo?): String {
        return videoInfo?.fulltitle?.takeIf { it.isNotBlank() }
            ?: videoInfo?.title?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.choose_quality_title)
    }

    fun labelForDownload(context: Context, download: DownloadEntity): String {
        val selector = download.qualitySelector?.trim().orEmpty()
        return when {
            DownloadSourceClassifier.shouldUseHttpDownloader(download.sourceUrl) -> context.getString(R.string.download_direct)
            selector.isBlank() -> context.getString(R.string.custom_format)
            selector == SELECTOR_MP4_1440P -> context.getString(R.string.ytdlp_quality_mp4_1440p)
            selector == SELECTOR_MP4_1080P -> context.getString(R.string.ytdlp_quality_mp4_1080p)
            selector == SELECTOR_MP4_720P -> context.getString(R.string.ytdlp_quality_mp4_720p)
            selector == SELECTOR_MP4_480P -> context.getString(R.string.ytdlp_quality_mp4_480p)
            selector == SELECTOR_MP3_320K -> context.getString(R.string.ytdlp_quality_mp3_320k)
            selector == SELECTOR_MP3_256K -> context.getString(R.string.ytdlp_quality_mp3_256k)
            selector == SELECTOR_MP3_192K -> context.getString(R.string.ytdlp_quality_mp3_192k)
            selector == SELECTOR_MP3_128K -> context.getString(R.string.ytdlp_quality_mp3_128k)
            selector in LEGACY_SELECTORS -> context.getString(R.string.legacy_format)
            else -> context.getString(R.string.custom_format)
        }
    }

    private val LEGACY_SELECTORS = setOf(
        "best",
        "best[height<=720]",
        "best[height<=480]",
        "best[height<=360]",
        "mp3",
        "bestaudio"
    )
}
