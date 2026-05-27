package com.androiddownload.ui.player

data class PlayerPositionSnapshot(
    val positionMs: Int,
    val durationMs: Int,
    val progress: Int,
    val currentTimeLabel: String,
    val durationLabel: String,
    val hasDuration: Boolean,
    val isSeekable: Boolean
) {
    companion object {
        fun from(
            positionMs: Int,
            durationMs: Int,
            maxProgress: Int
        ): PlayerPositionSnapshot {
            val hasDuration = durationMs > 0
            return PlayerPositionSnapshot(
                positionMs = positionMs,
                durationMs = durationMs,
                progress = PlayerProgressCalculator.positionToProgress(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    maxProgress = maxProgress
                ),
                currentTimeLabel = PlayerProgressCalculator.formatPlaybackTime(positionMs),
                durationLabel = PlayerProgressCalculator.formatPlaybackTime(durationMs),
                hasDuration = hasDuration,
                isSeekable = hasDuration
            )
        }
    }
}
