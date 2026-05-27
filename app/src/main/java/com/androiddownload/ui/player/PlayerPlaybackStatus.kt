package com.androiddownload.ui.player

/**
 * Conceptual playback status for a future player state/engine.
 *
 * This does not represent player session/list state, UI/fullscreen state, or
 * carry MediaPlayer/VideoView instances. It is not connected to runtime yet.
 */
enum class PlayerPlaybackStatus {
    IDLE,
    PREPARING,
    READY,
    PLAYING,
    PAUSED,
    STOPPED,
    COMPLETED,
    ERROR,
    RELEASED
}
