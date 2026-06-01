package com.androiddownload.ui.player

import android.net.Uri

class InlineVideoPlaybackController(
    private val videoView: AspectRatioVideoView,
    private val shouldHandleCallbacks: () -> Boolean,
    private val onPrepared: () -> Unit,
    private val onCompleted: () -> Unit,
    private val onError: () -> Unit
) {
    private var prepared = false
    private var playbackGeneration = 0

    fun start(
        uri: Uri,
        positionMs: Int = 0,
        playWhenReady: Boolean = true,
        onStartError: (() -> Unit)? = null
    ) {
        val generation = ++playbackGeneration
        prepared = false
        try {
            videoView.setOnPreparedListener {
                if (!isCurrentGeneration(generation) || !shouldHandleCallbacks()) {
                    return@setOnPreparedListener
                }
                prepared = true
                videoView.setVideoSize(it.videoWidth, it.videoHeight)
                if (positionMs > 0) {
                    videoView.seekTo(positionMs)
                }
                if (playWhenReady) {
                    videoView.start()
                }
                onPrepared()
            }
            videoView.setOnCompletionListener {
                if (isCurrentGeneration(generation) && shouldHandleCallbacks()) {
                    onCompleted()
                }
            }
            videoView.setOnErrorListener { _, _, _ ->
                if (!isCurrentGeneration(generation) || !shouldHandleCallbacks()) {
                    return@setOnErrorListener true
                }
                onStartError?.invoke() ?: onError()
                true
            }
            videoView.setVideoURI(uri)
        } catch (exception: Exception) {
            if (isCurrentGeneration(generation)) {
                onStartError?.invoke()
            }
        }
    }

    fun pause() {
        if (videoView.isPlaying) {
            videoView.pause()
        }
    }

    fun resume() {
        if (prepared) {
            videoView.start()
        }
    }

    fun stop() {
        playbackGeneration++
        runCatching { videoView.stopPlayback() }
        runCatching { videoView.suspend() }
        prepared = false
    }

    fun release() {
        stop()
    }

    fun seekTo(positionMs: Int) {
        if (prepared) {
            videoView.seekTo(positionMs)
        }
    }

    fun isPlaying(): Boolean {
        return videoView.isPlaying
    }

    fun isPrepared(): Boolean {
        return prepared
    }

    fun duration(): Int {
        return if (prepared) videoView.duration.takeIf { it > 0 } ?: 0 else 0
    }

    fun currentPosition(): Int {
        return if (prepared) videoView.currentPosition else 0
    }

    private fun isCurrentGeneration(generation: Int): Boolean {
        return generation == playbackGeneration
    }
}
