package com.androiddownload.ui.player

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri

class AudioPlaybackController(
    private val context: Context,
    private val onPrepared: () -> Unit,
    private val onCompleted: () -> Unit,
    private val onError: () -> Unit
) {
    private var mediaPlayer: MediaPlayer? = null
    private var prepared = false

    fun start(uri: Uri) {
        stop()
        prepared = false
        val player = MediaPlayer()
        mediaPlayer = player
        player.apply {
            setOnPreparedListener { player ->
                prepared = true
                player.start()
                onPrepared()
            }
            setOnCompletionListener { onCompleted() }
            setOnErrorListener { _, _, _ ->
                stop()
                onError()
                true
            }
            try {
                setDataSource(context, uri)
                prepareAsync()
            } catch (exception: Exception) {
                runCatching { release() }
                if (mediaPlayer === this) {
                    mediaPlayer = null
                }
                prepared = false
                onError()
            }
        }
    }

    fun pause() {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        }
    }

    fun resume() {
        if (prepared) {
            mediaPlayer?.start()
        }
    }

    fun stop() {
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        prepared = false
    }

    fun release() {
        stop()
    }

    fun seekTo(positionMs: Int) {
        if (prepared) {
            mediaPlayer?.seekTo(positionMs)
        }
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }

    fun isPrepared(): Boolean {
        return prepared && mediaPlayer != null
    }

    fun duration(): Int {
        return if (prepared) mediaPlayer?.duration ?: 0 else 0
    }

    fun currentPosition(): Int {
        return if (prepared) mediaPlayer?.currentPosition ?: 0 else 0
    }
}
