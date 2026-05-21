package com.androiddownload.ui.downloads

import android.graphics.BitmapFactory
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class MediaThumbnailLoader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun load(url: String?, target: ImageView, onLoaded: () -> Unit) {
        val cleanUrl = url?.trim().orEmpty()
        if (cleanUrl.isBlank()) return

        target.tag = cleanUrl
        scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                runCatching {
                    val request = Request.Builder().url(cleanUrl).get().build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("Thumbnail HTTP ${response.code}")
                        response.body?.byteStream()?.use(BitmapFactory::decodeStream)
                    }
                }.getOrNull()
            } ?: return@launch

            if (target.tag == cleanUrl) {
                target.setImageBitmap(bitmap)
                onLoaded()
            }
        }
    }

    fun cancel() {
        scope.cancel()
    }
}
