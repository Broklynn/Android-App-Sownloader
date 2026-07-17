package com.androiddownload.core.utils

data class VideoCompatibilityProfile(
    val selector: String,
    val maxHeight: Int,
    val maxFps: Int,
    val container: String,
    val videoCodec: String,
    val audioCodec: String,
    val pixelFormat: String
) {
    companion object {
        const val SELECTOR_CAR_COMPATIBLE_720P = "car-compatible:720p"

        val CAR_COMPATIBLE_720P = VideoCompatibilityProfile(
            selector = SELECTOR_CAR_COMPATIBLE_720P,
            maxHeight = 720,
            maxFps = 30,
            container = "mp4",
            videoCodec = "h264",
            audioCodec = "aac",
            pixelFormat = "yuv420p"
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
