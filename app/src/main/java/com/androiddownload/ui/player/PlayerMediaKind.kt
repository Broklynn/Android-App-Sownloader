package com.androiddownload.ui.player

/**
 * Media type a future playback engine can handle.
 *
 * This is not a UI category, not an inline/fullscreen visual mode, and does not
 * classify downloads by extension or replace DownloadOpenRouter.
 */
enum class PlayerMediaKind {
    AUDIO,
    VIDEO
}
