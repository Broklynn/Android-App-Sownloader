package com.androiddownload.download.http

import org.json.JSONObject
import java.util.Locale

object HttpHeadersJsonParser {
    fun parse(httpHeadersJson: String?): Map<String, String> {
        val rawJson = httpHeadersJson?.trim().orEmpty()
        if (rawJson.isBlank()) return emptyMap()

        val headers = runCatching { JSONObject(rawJson) }.getOrNull() ?: return emptyMap()
        val sanitized = linkedMapOf<String, String>()
        val keys = headers.keys()
        while (keys.hasNext()) {
            val rawName = keys.next()
            val canonicalName = canonicalAllowedHeaderName(rawName) ?: continue
            if (sanitized.containsKey(canonicalName)) continue

            val value = headers.optString(rawName)
                .trim()
                .takeIf { it.isNotBlank() && isValidHeaderValue(it) }
                ?: continue
            sanitized[canonicalName] = value
        }
        return sanitized
    }

    private fun canonicalAllowedHeaderName(name: String): String? {
        val trimmed = name.trim()
        if (!isValidHeaderName(trimmed)) return null
        val normalized = trimmed.lowercase(Locale.US)
        if (SENSITIVE_HEADER_NAME_PARTS.any { normalized.contains(it) }) return null
        if (normalized.startsWith("x-ig-")) return null
        if (normalized in BLOCKED_HEADER_NAMES) return null
        return ALLOWED_HEADER_NAMES[normalized]
    }

    private fun isValidHeaderName(name: String): Boolean {
        if (name.isBlank()) return false
        return name.all { char -> char.code in 33..126 && char !in INVALID_HEADER_NAME_CHARS }
    }

    private fun isValidHeaderValue(value: String): Boolean {
        return value.none { char -> char == '\r' || char == '\n' || char.code == 0 }
    }

    private val ALLOWED_HEADER_NAMES = mapOf(
        "user-agent" to "User-Agent",
        "accept" to "Accept",
        "accept-language" to "Accept-Language",
        "referer" to "Referer",
        "sec-fetch-mode" to "Sec-Fetch-Mode"
    )
    private val BLOCKED_HEADER_NAMES = setOf(
        "cookie",
        "authorization",
        "proxy-authorization",
        "x-csrftoken",
        "x-asbd-id",
        "x-mid",
        "range",
        "host",
        "connection",
        "content-length",
        "transfer-encoding",
        "content-encoding"
    )
    private val SENSITIVE_HEADER_NAME_PARTS = setOf(
        "token",
        "auth",
        "session",
        "cookie",
        "credential"
    )
    private val INVALID_HEADER_NAME_CHARS = setOf(
        '(', ')', '<', '>', '@', ',', ';', ':', '\\', '"', '/', '[', ']', '?', '=', '{', '}', ' ', '\t'
    )
}
