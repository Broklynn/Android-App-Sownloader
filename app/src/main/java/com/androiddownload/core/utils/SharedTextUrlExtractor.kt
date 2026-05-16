package com.androiddownload.core.utils

object SharedTextUrlExtractor {
    fun extract(sharedText: String): String? {
        return extract(sharedText, UrlValidator::isValidHttpUrl)
    }

    internal fun extract(
        sharedText: String,
        isValidHttpUrl: (String) -> Boolean
    ): String? {
        val trimmedText = sharedText.trim()
        if (isValidHttpUrl(trimmedText)) {
            return trimmedText
        }

        return SHARED_URL_PATTERN.find(trimmedText)
            ?.value
            ?.trimEnd('.', ',', ';', ':', ')', ']', '}', '>')
            ?.takeIf { isValidHttpUrl(it) }
    }

    private val SHARED_URL_PATTERN = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
}
