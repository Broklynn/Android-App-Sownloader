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
                if (!released) {
                    prepared = false
                    onError()
                }
            }
        })
    }

    fun attach(playerView: PlayerView) {
        playerView.player = player
    }

    fun detach(playerView: PlayerView) {
        if (playerView.player === player) {
            playerView.player = null
        }
    }

    fun start(uri: Uri, positionMs: Long = 0L, playWhenReady: Boolean = true) {
        prepared = false
        preparedNotified = false
        completedNotified = false
        player.setMediaItem(MediaItem.fromUri(uri), positionMs)
        player.playWhenReady = playWhenReady
        player.prepare()
    }

    fun pause() {
        player.pause()
    }

    fun resume() {
        if (prepared) {
            player.play()
        }
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
        prepared = false
        preparedNotified = false
        completedNotified = false
    }

    fun release() {
        if (!released) {
            released = true
            player.release()
            prepared = false
        }
    }

    fun seekTo(positionMs: Long) {
        if (prepared) {
            player.seekTo(positionMs)
        }
    }

    fun isPlaying(): Boolean {
        return player.isPlaying
    }

    fun isPrepared(): Boolean {
        return prepared
    }

    fun duration(): Long {
        return player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
    }

    fun currentPosition(): Long {
        return player.currentPosition.coerceAtLeast(0L)
    }
}
