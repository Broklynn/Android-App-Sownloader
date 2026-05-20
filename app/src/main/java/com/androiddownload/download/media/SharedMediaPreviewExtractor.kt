package com.androiddownload.download.media

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SharedMediaPreviewExtractor(
    context: Context
) {
    private val appContext = context.applicationContext

    suspend fun extract(url: String): SharedMediaPreview {
        return withContext(Dispatchers.IO) {
            YoutubeDL.getInstance().init(appContext)
            val request = YoutubeDLRequest(url).apply {
                addOption("--dump-single-json")
                addOption("--skip-download")
            }
            val response = YoutubeDL.getInstance().execute(request, PREVIEW_PROCESS_ID)
            SharedMediaPreviewParser.parse(url, response.out)
        }
    }

    private companion object {
        const val PREVIEW_PROCESS_ID = "shared-media-preview"
    }
}
