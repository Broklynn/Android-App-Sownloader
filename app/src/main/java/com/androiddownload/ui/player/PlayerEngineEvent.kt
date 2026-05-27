package com.androiddownload.ui.player

/**
 * Conceptual events for a future playback engine.
 *
 * These are not real callbacks yet, do not know about UI, session/list,
 * fullscreen, or Android runtime, and are not connected to the current player.
 */
sealed class PlayerEngineEvent {
    data class StateChanged(
        val state: PlayerEngineState
    ) : PlayerEngineEvent()

    data class Prepared(
        val state: PlayerEngineState
    ) : PlayerEngineEvent()

    data class PositionChanged(
        val snapshot: PlayerPositionSnapshot
    ) : PlayerEngineEvent()

    data class Completed(
        val state: PlayerEngineState
    ) : PlayerEngineEvent()

    data class Error(
        val state: PlayerEngineState,
        val message: String?
    ) : PlayerEngineEvent()
}
