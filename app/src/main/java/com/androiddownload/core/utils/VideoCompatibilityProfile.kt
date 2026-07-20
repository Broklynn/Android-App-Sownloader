package com.androiddownload.core.utils

data class VideoCompatibilityProfile(
    val selector: String,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxFps: Int,
    val container: String,
    val videoCodec: String,
    val audioCodec: String,
    val pixelFormat: String,
    val maxVideoLevel: Int,
    val maxVideoBitrate: Long,
    val audioEncodingTargetBitrate: Long,
    val audioCompatibilityBitrateCeiling: Long
) {
    companion object {
        const val SELECTOR_CAR_COMPATIBLE_720P = "car-compatible:720p"
        const val CAR_AUDIO_ENCODING_TARGET_BITRATE = 192_000L
        const val CAR_AUDIO_COMPATIBILITY_BITRATE_CEILING = 200_000L

        val CAR_COMPATIBLE_720P = VideoCompatibilityProfile(
            selector = SELECTOR_CAR_COMPATIBLE_720P,
            maxWidth = 1280,
            maxHeight = 720,
            maxFps = 30,
            container = "mp4",
            videoCodec = "h264",
            audioCodec = "aac",
            pixelFormat = "yuv420p",
            maxVideoLevel = 31,
            maxVideoBitrate = 10_000_000L,
            audioEncodingTargetBitrate = CAR_AUDIO_ENCODING_TARGET_BITRATE,
            audioCompatibilityBitrateCeiling = CAR_AUDIO_COMPATIBILITY_BITRATE_CEILING
        )

        fun fromSelector(selector: String?): VideoCompatibilityProfile? {
            return when (selector?.trim()) {
                SELECTOR_CAR_COMPATIBLE_720P -> CAR_COMPATIBLE_720P
                else -> null
            }
        }

        fun isEnabled(selector: String?): Boolean {
            return fromSelector(selector) != null
        }
    }
}
