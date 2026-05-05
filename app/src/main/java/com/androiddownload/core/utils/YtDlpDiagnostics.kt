package com.androiddownload.core.utils

import android.content.Context
import android.net.Uri
import com.androiddownload.R
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale

object YtDlpDiagnostics {
    private const val PREFS_NAME = "aio_downloader_settings"
    private const val KEY_EVENTS = "ytdlp_diagnostics_events"
    private const val MAX_EVENTS = 20

    fun record(
        context: Context,
        url: String,
        option: String,
        attempt: String,
        result: String,
        error: String? = null,
        autoUpdate: Boolean = false
    ) {
        val event = JSONObject().apply {
            put("time", System.currentTimeMillis())
            put("type", "yt-dlp")
            put("url", summarizeUrl(url))
            put("option", option.ifBlank { "best" })
            put("attempt", attempt)
            put("error", summarizeError(error))
            put("autoUpdate", autoUpdate)
            put("result", result)
        }
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = runCatching {
            JSONArray(prefs.getString(KEY_EVENTS, "[]").orEmpty())
        }.getOrDefault(JSONArray())
        val updated = JSONArray().apply {
            put(event)
            for (index in 0 until minOf(current.length(), MAX_EVENTS - 1)) {
                put(current.getJSONObject(index))
            }
        }
        prefs.edit().putString(KEY_EVENTS, updated.toString()).apply()
    }

    fun formatted(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val events = runCatching {
            JSONArray(prefs.getString(KEY_EVENTS, "[]").orEmpty())
        }.getOrDefault(JSONArray())
        if (events.length() == 0) return context.getString(R.string.diagnostics_empty)
        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
        return buildString {
            for (index in 0 until events.length()) {
                val event = events.getJSONObject(index)
                appendLine(dateFormat.format(Date(event.optLong("time"))))
                appendLine("${event.optString("type")} | ${event.optString("result")}")
                appendLine("URL: ${event.optString("url")}")
                appendLine("Opcao: ${event.optString("option")}")
                appendLine("Tentativa: ${event.optString("attempt")}")
                appendLine("Auto-update: ${if (event.optBoolean("autoUpdate")) "sim" else "nao"}")
                event.optString("error").takeIf { it.isNotBlank() }?.let {
                    appendLine("Erro: $it")
                }
                if (index < events.length() - 1) appendLine()
            }
        }.trim()
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_EVENTS)
            .apply()
    }

    private fun summarizeUrl(url: String): String {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        val host = uri?.host?.lowercase(Locale.US).orEmpty()
        val path = uri?.lastPathSegment?.takeIf { it.isNotBlank() }.orEmpty()
        return when {
            host.isBlank() -> "URL"
            path.isBlank() -> host
            else -> "$host/.../$path"
        }
    }

    private fun summarizeError(error: String?): String {
        val clean = error.orEmpty()
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        return clean.take(220)
    }
}
