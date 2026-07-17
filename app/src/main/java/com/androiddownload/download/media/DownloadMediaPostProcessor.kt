package com.androiddownload.download.media

import com.androiddownload.core.utils.FileNameUtils
import com.androiddownload.core.utils.VideoCompatibilityProfile
import java.io.File

class DownloadMediaPostProcessor(
    private val carCompatibilityTranscoder: CarCompatibilityTranscoder
) {
    fun process(
        inputFile: File,
        preferredName: String,
        mimeType: String?,
        qualitySelector: String?
    ): Result {
        val profile = VideoCompatibilityProfile.fromSelector(qualitySelector)
            ?: return Result.Original(inputFile, preferredName, mimeType)
        if (mimeType != "video/mp4") {
            return Result.Original(inputFile, preferredName, mimeType)
        }

        val outputName = carCompatibleName(preferredName)
        val outputFile = uniqueSiblingFile(inputFile, outputName)
        return when (val transcodeResult = carCompatibilityTranscoder.transcode(inputFile, outputFile, profile)) {
            is CarCompatibilityTranscoder.TranscodeResult.Success -> Result.Processed(
                file = transcodeResult.outputFile,
                preferredName = outputName,
                mimeType = "video/mp4"
            )
            is CarCompatibilityTranscoder.TranscodeResult.Skipped -> Result.Fallback(
                file = inputFile,
                preferredName = preferredName,
                mimeType = mimeType,
                reason = transcodeResult.reason
            )
            is CarCompatibilityTranscoder.TranscodeResult.Failure -> Result.Fallback(
                file = inputFile,
                preferredName = preferredName,
                mimeType = mimeType,
                reason = transcodeResult.message
            )
        }
    }

    internal fun carCompatibleName(preferredName: String): String {
        val sanitized = FileNameUtils.sanitize(preferredName)
        val baseName = sanitized.substringBeforeLast('.', sanitized).ifBlank { "video" }
        return "$baseName - carro.mp4"
    }

    private fun uniqueSiblingFile(inputFile: File, preferredName: String): File {
        val parent = inputFile.parentFile ?: File(".")
        val baseName = preferredName.substringBeforeLast('.', preferredName)
        val extension = preferredName.substringAfterLast('.', missingDelimiterValue = "")
        var candidate = File(parent, preferredName)
        var index = 1
        while (candidate.exists()) {
            val nextName = if (extension.isBlank()) {
                "$baseName ($index)"
            } else {
                "$baseName ($index).$extension"
            }
            candidate = File(parent, nextName)
            index++
        }
        return candidate
    }

    sealed class Result {
        abstract val file: File
        abstract val preferredName: String
        abstract val mimeType: String?

        data class Original(
            override val file: File,
            override val preferredName: String,
            override val mimeType: String?
        ) : Result()

        data class Processed(
            override val file: File,
            override val preferredName: String,
            override val mimeType: String?
        ) : Result()

        data class Fallback(
            override val file: File,
            override val preferredName: String,
            override val mimeType: String?,
            val reason: String
        ) : Result()
    }
}
