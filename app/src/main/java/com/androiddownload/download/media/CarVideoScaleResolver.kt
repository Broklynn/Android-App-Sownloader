package com.androiddownload.download.media

import com.androiddownload.core.utils.VideoCompatibilityProfile
import kotlin.math.roundToInt

internal object CarVideoScaleResolver {
    fun buildFilter(profile: VideoCompatibilityProfile): String {
        return "scale='min(${profile.maxWidth},iw)':'min(${profile.maxHeight},ih)':" +
            "force_original_aspect_ratio=decrease:force_divisible_by=2," +
            "fps=${profile.maxFps},format=${profile.pixelFormat}"
    }

    fun resolve(
        inputWidth: Int,
        inputHeight: Int,
        profile: VideoCompatibilityProfile
    ): VideoSize {
        require(inputWidth > 0 && inputHeight > 0) {
            "Video dimensions must be positive."
        }

        val targetWidth = minOf(profile.maxWidth, inputWidth)
        val targetHeight = minOf(profile.maxHeight, inputHeight)
        val scale = minOf(
            1.0,
            targetWidth.toDouble() / inputWidth,
            targetHeight.toDouble() / inputHeight
        )
        return VideoSize(
            width = evenNearestWithin(inputWidth * scale, targetWidth),
            height = evenNearestWithin(inputHeight * scale, targetHeight)
        )
    }

    private fun evenNearestWithin(value: Double, maximum: Int): Int {
        val nearestEven = (value / 2.0).roundToInt() * 2
        val maximumEven = maximum - maximum.mod(2)
        return minOf(nearestEven, maximumEven)
    }

    data class VideoSize(
        val width: Int,
        val height: Int
    )
}
