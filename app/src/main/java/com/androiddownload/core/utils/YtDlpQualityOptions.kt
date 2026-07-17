package com.androiddownload.core.utils

import android.content.Context
import com.androiddownload.R
import com.androiddownload.core.model.DownloadEntity
import com.yausername.youtubedl_android.mapper.VideoInfo

data class YtDlpQualityOption(
    val label: String,
    val formatSelector: String
)

data class YtDlpQualityLabelTexts(
    val direct: String,
    val custom: String,
    val legacy: String,
    val mp4_1440p: String,
    val mp4_1080p: String,
    val mp4_720p: String,
    val mp4_car_720p: String,
    val mp4_480p: String,
    val mp3_320k: String,
    val mp3_256k: String,
    val mp3_192k: String,
    val mp3_128k: String
)

object YtDlpQualityOptions {
    const val SELECTOR_MP4_1440P = "bestvideo[height<=1440][ext=mp4]+bestaudio[ext=m4a]/best[height<=1440][ext=mp4]/best[height<=1440]"
    const val SELECTOR_MP4_1080P = "bestvideo[height<=1080][ext=mp4]+bestaudio[ext=m4a]/best[height<=1080][ext=mp4]/best[height<=1080]"
    const val SELECTOR_MP4_720P = "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720][ext=mp4]/best[height<=720]"
    const val SELECTOR_MP4_480P = "bestvideo[height<=480][ext=mp4]+bestaudio[ext=m4a]/best[height<=480][ext=mp4]/best[height<=480]"
    const val SELECTOR_MP4_CAR_COMPATIBLE_720P = VideoCompatibilityProfile.SELECTOR_CAR_COMPATIBLE_720P
    const val SELECTOR_MP3_320K = "mp3:320K"
    const val SELECTOR_MP3_256K = "mp3:256K"
    const val SELECTOR_MP3_192K = "mp3:192K"
    const val SELECTOR_MP3_128K = "mp3:128K"

    fun build(context: Context, videoInfo: VideoInfo?): List<YtDlpQualityOption> {
        return listOf(
            YtDlpQualityOption(context.getString(R.string.ytdlp_quality_mp4_1440p), SELECTOR_MP4_1440P),
            YtDlpQualityOption(context.getString(R.string.ytdlp_quality_mp4_1080p), SELECTOR_MP4_1080P),
            YtDlpQualityOption(context.getString(R.string.ytdlp_quality_mp4_720p), SELECTOR_MP4_720P),
            YtDlpQualityOption(context.getString(R.string.ytdlp_quality_mp4_car_720p), SELECTOR_MP4_CAR_COMPATIBLE_720P),
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
        return labelForDownload(
            qualitySelector = download.qualitySelector,
            sourceUrl = download.sourceUrl,
            labels = YtDlpQualityLabelTexts(
                direct = context.getString(R.string.download_direct),
                custom = context.getString(R.string.custom_format),
                legacy = context.getString(R.string.legacy_format),
                mp4_1440p = context.getString(R.string.ytdlp_quality_mp4_1440p),
                mp4_1080p = context.getString(R.string.ytdlp_quality_mp4_1080p),
                mp4_720p = context.getString(R.string.ytdlp_quality_mp4_720p),
                mp4_car_720p = context.getString(R.string.ytdlp_quality_mp4_car_720p),
                mp4_480p = context.getString(R.string.ytdlp_quality_mp4_480p),
                mp3_320k = context.getString(R.string.ytdlp_quality_mp3_320k),
                mp3_256k = context.getString(R.string.ytdlp_quality_mp3_256k),
                mp3_192k = context.getString(R.string.ytdlp_quality_mp3_192k),
                mp3_128k = context.getString(R.string.ytdlp_quality_mp3_128k)
            )
        )
    }

    fun labelForDownload(
        qualitySelector: String?,
        sourceUrl: String,
        labels: YtDlpQualityLabelTexts
    ): String {
        val selector = qualitySelector?.trim().orEmpty()
        return when {
            DownloadSourceClassifier.shouldUseHttpDownloader(sourceUrl) -> labels.direct
            selector.isBlank() -> labels.custom
            selector == SELECTOR_MP4_1440P -> labels.mp4_1440p
            selector == SELECTOR_MP4_1080P -> labels.mp4_1080p
            selector == SELECTOR_MP4_720P -> labels.mp4_720p
            selector == SELECTOR_MP4_CAR_COMPATIBLE_720P -> labels.mp4_car_720p
            selector == SELECTOR_MP4_480P -> labels.mp4_480p
            selector == SELECTOR_MP3_320K -> labels.mp3_320k
            selector == SELECTOR_MP3_256K -> labels.mp3_256k
            selector == SELECTOR_MP3_192K -> labels.mp3_192k
            selector == SELECTOR_MP3_128K -> labels.mp3_128k
            selector in LEGACY_SELECTORS -> labels.legacy
            else -> labels.custom
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
