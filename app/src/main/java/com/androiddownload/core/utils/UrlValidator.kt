package com.androiddownload.core.utils

import android.net.Uri

object UrlValidator {
    fun isValidHttpUrl(value: String): Boolean {
        val uri = Uri.parse(value)
        val scheme = uri.scheme?.lowercase()
        return (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
    }
}
