package com.androiddownload.ui.player

/**
 * Generic event sink for conceptual events from a future playback engine.
 *
 * This receives PlayerEngineEvent values, does not define specific callbacks,
 * does not know about UI, session/list, fullscreen, or Android runtime, and is
 * not connected to the current player.
 */
fun interface PlayerEngineEventListener {
    fun onPlayerEngineEvent(event: PlayerEngineEvent)
}
