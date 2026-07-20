package com.androiddownload.download.media

import org.json.JSONArray
import org.json.JSONObject

internal sealed interface CarMediaMetadataParseResult {
    data class Success(
        val metadata: CarMediaMetadata
    ) : CarMediaMetadataParseResult

    data object Failure : CarMediaMetadataParseResult
}

internal data class CarMediaMetadata(
    val streams: List<CarMediaStreamMetadata>,
    val format: CarMediaFormatMetadata
)

internal data class CarMediaStreamMetadata(
    val index: String?,
    val codecType: String?,
    val codecName: String?,
    val profile: String?,
    val level: String?,
    val width: String?,
    val height: String?,
    val pixelFormat: String?,
    val averageFrameRate: String?,
    val realFrameRate: String?,
    val sampleAspectRatio: String?,
    val displayAspectRatio: String?,
    val fieldOrder: String?,
    val bitRate: String?,
    val sampleRate: String?,
    val channels: String?,
    val channelLayout: String?,
    val attachedPicture: String?,
    val rotations: List<String>
)

internal data class CarMediaFormatMetadata(
    val formatName: String?,
    val duration: String?,
    val bitRate: String?,
    val streamCount: String?
)

internal object CarMediaMetadataParser {
    fun parse(json: String): CarMediaMetadataParseResult {
        if (json.isBlank()) return CarMediaMetadataParseResult.Failure
        return try {
            val root = JSONObject(json)
            val streamsJson = root.optJSONArray("streams")
                ?: return CarMediaMetadataParseResult.Failure
            val formatJson = root.optJSONObject("format")
                ?: return CarMediaMetadataParseResult.Failure

            val streams = buildList {
                for (index in 0 until streamsJson.length()) {
                    val stream = streamsJson.optJSONObject(index)
                        ?: return CarMediaMetadataParseResult.Failure
                    add(stream.toMetadata())
                }
            }
            CarMediaMetadataParseResult.Success(
                CarMediaMetadata(
                    streams = streams,
                    format = CarMediaFormatMetadata(
                        formatName = formatJson.optionalText("format_name"),
                        duration = formatJson.optionalText("duration"),
                        bitRate = formatJson.optionalText("bit_rate"),
                        streamCount = formatJson.optionalText("nb_streams")
                    )
                )
            )
        } catch (_: Exception) {
            CarMediaMetadataParseResult.Failure
        }
    }

    private fun JSONObject.toMetadata(): CarMediaStreamMetadata {
        return CarMediaStreamMetadata(
            index = optionalText("index"),
            codecType = optionalText("codec_type"),
            codecName = optionalText("codec_name"),
            profile = optionalText("profile"),
            level = optionalText("level"),
            width = optionalText("width"),
            height = optionalText("height"),
            pixelFormat = optionalText("pix_fmt"),
            averageFrameRate = optionalText("avg_frame_rate"),
            realFrameRate = optionalText("r_frame_rate"),
            sampleAspectRatio = optionalText("sample_aspect_ratio"),
            displayAspectRatio = optionalText("display_aspect_ratio"),
            fieldOrder = optionalText("field_order"),
            bitRate = optionalText("bit_rate"),
            sampleRate = optionalText("sample_rate"),
            channels = optionalText("channels"),
            channelLayout = optionalText("channel_layout"),
            attachedPicture = optJSONObject("disposition")?.optionalText("attached_pic"),
            rotations = rotations()
        )
    }

    private fun JSONObject.rotations(): List<String> {
        val rotations = mutableListOf<String>()
        optJSONObject("tags")?.let { tags ->
            if (tags.has("rotate")) {
                rotations += tags.optionalText("rotate").orEmpty()
            }
        }
        optJSONArray("side_data_list")?.forEachObject { sideData ->
            if (sideData.has("rotation")) {
                rotations += sideData.optionalText("rotation").orEmpty()
            }
        }
        return rotations
    }

    private fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
        for (index in 0 until length()) {
            optJSONObject(index)?.let(block)
        }
    }

    private fun JSONObject.optionalText(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return opt(name)?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" }
    }
}
