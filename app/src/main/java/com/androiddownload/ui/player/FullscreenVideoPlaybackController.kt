package com.androiddownload.ui.player

import android.net.Uri

class FullscreenVideoPlaybackController(
    private val videoView: AspectRatioVideoView,
    private val onPrepared: () -> Unit,
    private val onCompleted: () -> Unit,
    private val onError: () -> Unit
) {
    private var prepared = false

    fun start(uri: Uri, positionMs: Int = 0, playWhenReady: Boolean = true) {
        prepared = false
        try {
            videoView.setOnPreparedListener {
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
                prepared = false
                onCompleted()
            }
            videoView.setOnErrorListener { _, _, _ ->
                prepared = false
                onError()
                true
            }
            videoView.setVideoURI(uri)
        } catch (exception: Exception) {
            prepared = false
            runCatching { videoView.stopPlayback() }
            runCatching { videoView.suspend() }
            onError()
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
}
