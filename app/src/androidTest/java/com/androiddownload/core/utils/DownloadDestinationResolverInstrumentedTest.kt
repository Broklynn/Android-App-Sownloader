package com.androiddownload.core.utils

import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadDestinationResolverInstrumentedTest {
    @Test
    fun rejectedCompletionCleanupDeletesOnlyExactSavedFileReference() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = context.cacheDir.resolve("destination-cleanup-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val attemptFile = directory.resolve("video.mp4").apply { writeText("attempt") }
        val preexistingNeighbor = directory.resolve("video (1).mp4").apply { writeText("winner") }

        try {
            val deleted = DownloadDestinationResolver.deleteSavedFile(
                context,
                DownloadDestinationResolver.SavedFile(
                    fileName = attemptFile.name,
                    uri = Uri.fromFile(attemptFile),
                    bytes = attemptFile.length()
                )
            )

            assertTrue(deleted)
            assertFalse(attemptFile.exists())
            assertTrue(preexistingNeighbor.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun rejectedCompletionCleanupDeletesExactMediaStoreReference() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = requireNotNull(
            resolver.insert(
                collection,
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "darkwave-cleanup-${System.nanoTime()}.bin")
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/DarkWave/Test/"
                    )
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            )
        )

        try {
            resolver.openOutputStream(uri, "w")?.use { it.write(byteArrayOf(1, 2, 3)) }
            val deleted = DownloadDestinationResolver.deleteSavedFile(
                context,
                DownloadDestinationResolver.SavedFile(
                    fileName = "attempt.bin",
                    uri = uri,
                    bytes = 3
                )
            )

            assertTrue(deleted)
            assertFalse(contentExists(uri))
        } finally {
            resolver.delete(uri, null, null)
        }
    }

    private fun contentExists(uri: Uri): Boolean {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns._ID),
                null,
                null,
                null
            )?.use { cursor -> cursor.moveToFirst() } ?: false
        }.getOrDefault(false)
    }
}
