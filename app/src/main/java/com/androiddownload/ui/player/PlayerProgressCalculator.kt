package com.androiddownload.ui.player

import java.util.Locale

object PlayerProgressCalculator {
    fun formatPlaybackTime(milliseconds: Int): String {
        val totalSeconds = (milliseconds / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(Locale.US, minutes, seconds)
    }

    fun positionToProgress(
        positionMs: Int,
        durationMs: Int,
        maxProgress: Int
    ): Int {
        if (durationMs <= 0 || maxProgress <= 0) return 0
        return ((positionMs.toLong() * maxProgress) / durationMs)
            .coerceIn(0L, maxProgress.toLong())
            .toInt()
    }

    fun progressToPosition(
        progress: Int,
        durationMs: Int,
        maxProgress: Int
    ): Int {
        if (durationMs <= 0 || maxProgress <= 0) return 0
        return ((progress.toLong().coerceIn(0L, maxProgress.toLong()) * durationMs) / maxProgress)
            .coerceIn(0L, durationMs.toLong())
            .toInt()
    }

    fun seekBy(
        currentPositionMs: Int,
        deltaMs: Int,
        durationMs: Int
    ): Int {
        if (durationMs <= 0) return 0
        return (currentPositionMs.toLong() + deltaMs)
            .coerceIn(0L, durationMs.toLong())
            .toInt()
    }
}
