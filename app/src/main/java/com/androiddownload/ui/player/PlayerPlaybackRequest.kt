package com.androiddownload.ui.player

data class PlayerPlaybackRequest(
    val playbackUri: String,
    val mediaKind: PlayerMediaKind,
    val startPositionMs: Int = 0,
    val playWhenReady: Boolean = true,
    val downloadId: Long? = null,
    val title: String? = null
)
