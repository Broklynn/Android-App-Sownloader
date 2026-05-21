package com.androiddownload.core.utils

import android.content.ClipboardManager
import android.content.Context

class ClipboardUrlReader(
    private val context: Context
) {
    fun readUrl(): String? {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null

        val text = clip.getItemAt(0)
            .coerceToText(context)
            ?.toString()
            .orEmpty()

        return SharedTextUrlExtractor.extract(text)
    }
}
