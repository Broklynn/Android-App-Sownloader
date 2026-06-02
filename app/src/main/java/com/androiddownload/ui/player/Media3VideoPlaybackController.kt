package com.androiddownload.ui.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class Media3VideoPlaybackController(
    context: Context,
    private val onPrepared: () -> Unit,
    private val onCompleted: () -> Unit,
    private val onError: () -> Unit
) {
    private val player = ExoPlayer.Builder(context.applicationContext).build()
    private var prepared = false
    private var preparedNotified = false
    private var completedNotified = false
    private var errorNotified = false
    private var onStartError: (() -> Unit)? = null
    private var released = false

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (released) {
                    return
                }
                when (playbackState) {
                    Player.STATE_READY -> {
                        prepared = true
                        if (!preparedNotified) {
                            preparedNotified = true
                            onPrepared()
                        }
                    }
                    Player.STATE_ENDED -> {
                        prepared = false
                        if (!completedNotified) {
                            completedNotified = true
                            onCompleted()
                        }
                    }
                    Player.STATE_IDLE -> prepared = false
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!released && !errorNotified) {
                    prepared = false
                    errorNotified = true
                    onStartError?.invoke() ?: onError()
                    onStartError = null
                }
            }
        })
    }

    fun attach(playerView: PlayerView) {
        if (released) {
            return
        }
        playerView.player = player
    }

    fun detach(playerView: PlayerView) {
        if (playerView.player === player) {
            playerView.player = null
        }
    }

    fun start(uri: Uri, positionMs: Int = 0, playWhenReady: Boolean = true, onStartError: (() -> Unit)? = null) {
        if (released) {
            return
        }
        this.onStartError = onStartError
        prepared = false
        preparedNotified = false
        completedNotified = false
        errorNotified = false
        player.setMediaItem(MediaItem.fromUri(uri), positionMs.toLong().coerceAtLeast(0L))
        player.playWhenReady = playWhenReady
        player.prepare()
    }

    fun pause() {
        if (released) {
            return
        }
        player.pause()
    }

    fun resume() {
        if (!released && prepared) {
            player.play()
        }
    }

    fun stop() {
        if (released) {
            return
        }
        player.stop()
        player.clearMediaItems()
        prepared = false
        preparedNotified = false
        completedNotified = false
        errorNotified = false
        onStartError = null
    }

    fun release() {
        if (!released) {
            released = true
            player.release()
            prepared = false
        }
    }

    fun seekTo(positionMs: Int) {
        if (!released && prepared) {
            player.seekTo(positionMs.toLong().coerceAtLeast(0L))
        }
    }

    fun isPlaying(): Boolean {
        return !released && player.isPlaying
    }

    fun isPrepared(): Boolean {
        return prepared
    }

    fun duration(): Int {
        if (released) {
            return 0
        }
        return player.duration.takeIf { it != C.TIME_UNSET && it > 0L }?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0
    }

    fun currentPosition(): Int {
        if (released) {
            return 0
        }
        return player.currentPosition.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
