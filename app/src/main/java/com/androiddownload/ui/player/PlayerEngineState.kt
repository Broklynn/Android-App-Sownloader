package com.androiddownload.ui.player

/**
 * Conceptual observable snapshot for a future playback engine.
 *
 * This is not player session/list state, not UI/fullscreen state, and does not
 * command playback. It is not connected to runtime yet.
 */
data class PlayerEngineState(
    val status: PlayerPlaybackStatus = PlayerPlaybackStatus.IDLE,
    val mediaKind: PlayerMediaKind? = null,
    val positionSnapshot: PlayerPositionSnapshot? = null,
    val errorMessage: String? = null,
    val downloadId: Long? = null,
    val title: String? = null
)
