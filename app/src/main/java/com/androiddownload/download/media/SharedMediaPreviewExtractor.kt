package com.androiddownload.download.media

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

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
            SharedMediaPreviewJsonMapper.map(url, response.out)
        }
    }

    private companion object {
        const val PREVIEW_PROCESS_ID = "shared-media-preview"
    }
}

object SharedMediaPreviewJsonMapper {
    fun map(originalUrl: String, jsonText: String): SharedMediaPreview {
        val root = JSONObject(jsonText)
        val title = root.optionalString("fulltitle")
            ?: root.optionalString("title")
        val entries = root.optJSONArray("entries")
        val items = if (entries != null && entries.length() > 0) {
            entries.toSharedMediaItems(originalUrl)
        } else {
            listOf(root.toSharedMediaItem(originalUrl, index = 1))
        }
        return SharedMediaPreview(
            originalUrl = originalUrl,
            title = title,
            items = items
        )
    }

    private fun JSONArray.toSharedMediaItems(originalUrl: String): List<SharedMediaItem> {
        val items = mutableListOf<SharedMediaItem>()
        for (position in 0 until length()) {
            val entry = optJSONObject(position) ?: continue
            items += entry.toSharedMediaItem(originalUrl, index = position + 1)
        }
        return items.ifEmpty {
            listOf(
                SharedMediaItem(
                    id = "item-1",
                    title = "Item 1",
                    index = 1,
                    type = SharedMediaType.UNKNOWN,
                    thumbnailUrl = null,
                    sourceUrl = originalUrl
                )
            )
        }
    }

    private fun JSONObject.toSharedMediaItem(originalUrl: String, index: Int): SharedMediaItem {
        val entrySourceUrl = optionalString("webpage_url")
            ?: optionalString("url")
            ?: originalUrl
        return SharedMediaItem(
            id = optionalString("id")
                ?: optionalString("display_id")
                ?: entrySourceUrl,
            title = optionalString("title")
                ?: optionalString("filename")
                ?: "Item $index",
            index = index,
            type = detectType(),
            thumbnailUrl = optionalString("thumbnail"),
            sourceUrl = entrySourceUrl
        )
    }

    private fun JSONObject.detectType(): SharedMediaType {
        val vcodec = optionalString("vcodec")?.lowercase(Locale.US)
        val acodec = optionalString("acodec")?.lowercase(Locale.US)
        val ext = optionalString("ext")?.lowercase(Locale.US)
        val mimeType = optionalString("mime_type")?.lowercase(Locale.US)

        if (mimeType?.startsWith("image/") == true || ext in IMAGE_EXTENSIONS) {
            return SharedMediaType.IMAGE
        }
        if (mimeType?.startsWith("video/") == true ||
            ext in VIDEO_EXTENSIONS ||
            (vcodec != null && vcodec != "none")
        ) {
            return SharedMediaType.VIDEO
        }
        if (mimeType?.startsWith("audio/") == true ||
            ext in AUDIO_EXTENSIONS ||
            (acodec != null && acodec != "none")
        ) {
            return SharedMediaType.AUDIO
        }
        return SharedMediaType.UNKNOWN
    }

    private fun JSONObject.optionalString(name: String): String? {
        return optString(name)
            .trim()
            .takeIf { it.isNotBlank() && it != "null" }
    }

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "avif")
    private val VIDEO_EXTENSIONS = setOf("mp4", "webm", "mov", "mkv", "m4v")
    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "opus", "wav", "aac", "flac")
}
