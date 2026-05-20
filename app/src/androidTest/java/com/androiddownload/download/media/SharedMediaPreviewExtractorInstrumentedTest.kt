package com.androiddownload.download.media

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedMediaPreviewExtractorInstrumentedTest {
    @Test
    fun extractsRealInstagramPreviewWhenManualUrlIsProvided() = runBlocking {
        assumeTrue(
            "Substitua TEST_INSTAGRAM_URL por um link publico de Instagram/carrossel antes de rodar este teste manual.",
            TEST_INSTAGRAM_URL != TODO_URL
        )

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preview = SharedMediaPreviewExtractor(context).extract(TEST_INSTAGRAM_URL)

        Log.i(TAG, "preview.title=${preview.title}")
        Log.i(TAG, "preview.items.size=${preview.items.size}")
        preview.items.forEach { item ->
            Log.i(
                TAG,
                "item index=${item.index}, title=${item.title}, type=${item.type}, " +
                    "sourceUrl=${item.sourceUrl}, thumbnailUrl=${item.thumbnailUrl}"
            )
        }
    }

    private companion object {
        const val TAG = "SharedMediaPreviewTest"
        const val TODO_URL = "TODO_REPLACE_WITH_PUBLIC_INSTAGRAM_CAROUSEL_URL"
        const val TEST_INSTAGRAM_URL = TODO_URL
    }
}
