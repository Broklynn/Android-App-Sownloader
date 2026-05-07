package com.androiddownload.ui.player

import android.content.Context
import android.util.AttributeSet
import android.widget.VideoView

class AspectRatioVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : VideoView(context, attrs, defStyleAttr) {
    private var videoWidth = 0
    private var videoHeight = 0

    fun setVideoSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        videoWidth = width
        videoHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val containerWidth = MeasureSpec.getSize(widthMeasureSpec)
        val containerHeight = MeasureSpec.getSize(heightMeasureSpec)

        if (videoWidth <= 0 || videoHeight <= 0 || containerWidth <= 0 || containerHeight <= 0) {
            setMeasuredDimension(containerWidth, containerHeight)
            return
        }

        val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()
        val containerRatio = containerWidth.toFloat() / containerHeight.toFloat()

        val measuredWidth: Int
        val measuredHeight: Int
        if (videoRatio > containerRatio) {
            measuredWidth = containerWidth
            measuredHeight = (containerWidth / videoRatio).toInt()
        } else {
            measuredHeight = containerHeight
            measuredWidth = (containerHeight * videoRatio).toInt()
        }

        setMeasuredDimension(measuredWidth, measuredHeight)
    }
}
