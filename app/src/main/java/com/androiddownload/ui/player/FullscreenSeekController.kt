package com.androiddownload.ui.player

class FullscreenSeekController(
    private val seekStepMs: Int = 10_000
) {
    fun deltaForTap(tapX: Float, width: Int): Int {
        return if (tapX < width / 2f) -seekStepMs else seekStepMs
    }

    fun targetPosition(
        currentPositionMs: Int,
        durationMs: Int,
        deltaMs: Int
    ): Int {
        return PlayerProgressCalculator.seekBy(
            currentPositionMs = currentPositionMs,
            deltaMs = deltaMs,
            durationMs = durationMs
        )
    }
}
