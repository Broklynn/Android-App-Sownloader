package com.androiddownload.download.media

import com.androiddownload.core.utils.VideoCompatibilityProfile
import java.math.BigDecimal
import java.math.BigInteger
import java.util.Locale

internal object CarMediaCompatibilityEvaluator {
    fun evaluate(
        metadata: CarMediaMetadata,
        profile: VideoCompatibilityProfile,
        requireMp4Container: Boolean
    ): CarMediaCompatibilityDecision {
        val reasons = linkedSetOf<CarMediaIncompatibilityReason>()
        validateFormat(metadata, requireMp4Container, reasons)
        validateStreamStructure(metadata, reasons)

        val videos = metadata.streams.filter { it.codecType.normalized() == "video" }
        val audios = metadata.streams.filter { it.codecType.normalized() == "audio" }
        videos.singleOrNull()?.let { validateVideo(it, profile, reasons) }
        audios.singleOrNull()?.let { validateAudio(it, profile, reasons) }

        return if (reasons.isEmpty()) {
            CarMediaCompatibilityDecision.Compatible
        } else {
            CarMediaCompatibilityDecision.Incompatible(reasons.toList())
        }
    }

    private fun validateFormat(
        metadata: CarMediaMetadata,
        requireMp4Container: Boolean,
        reasons: MutableSet<CarMediaIncompatibilityReason>
    ) {
        val format = metadata.format
        if (format.formatName.isNullOrBlank()) {
            reasons += CarMediaIncompatibilityReason.FORMAT_NAME
        }
        if (format.duration.toPositiveDecimal() == null) {
            reasons += CarMediaIncompatibilityReason.FORMAT_DURATION
        }
        if (format.bitRate.toPositiveLong() == null) {
            reasons += CarMediaIncompatibilityReason.FORMAT_BITRATE
        }
        if (format.streamCount.toPositiveInt() != metadata.streams.size) {
            reasons += CarMediaIncompatibilityReason.FORMAT_STREAM_COUNT
        }
        if (requireMp4Container) {
            val formatNames = format.formatName
                ?.lowercase(Locale.US)
                ?.split(',')
                ?.map(String::trim)
                ?.toSet()
                .orEmpty()
            if ("mp4" !in formatNames && "mov" !in formatNames) {
                reasons += CarMediaIncompatibilityReason.OUTPUT_CONTAINER
            }
        }
    }

    private fun validateStreamStructure(
        metadata: CarMediaMetadata,
        reasons: MutableSet<CarMediaIncompatibilityReason>
    ) {
        if (metadata.streams.size != 2) {
            reasons += CarMediaIncompatibilityReason.STREAM_STRUCTURE
        }
        val streamIndexes = metadata.streams.mapNotNull { it.index.toNonNegativeInt() }
        if (streamIndexes.size != metadata.streams.size ||
            streamIndexes.distinct().size != streamIndexes.size
        ) {
            reasons += CarMediaIncompatibilityReason.STREAM_INDEX
        }
        val videoCount = metadata.streams.count { it.codecType.normalized() == "video" }
        val audioCount = metadata.streams.count { it.codecType.normalized() == "audio" }
        if (videoCount != 1) {
            reasons += CarMediaIncompatibilityReason.VIDEO_STREAM_COUNT
        }
        if (audioCount != 1) {
            reasons += CarMediaIncompatibilityReason.AUDIO_STREAM_COUNT
        }
        if (metadata.streams.any { it.codecType.normalized() !in setOf("video", "audio") }) {
            reasons += CarMediaIncompatibilityReason.UNSUPPORTED_STREAM_TYPE
        }
    }

    private fun validateVideo(
        video: CarMediaStreamMetadata,
        profile: VideoCompatibilityProfile,
        reasons: MutableSet<CarMediaIncompatibilityReason>
    ) {
        if (video.codecName.normalized() != profile.videoCodec) {
            reasons += CarMediaIncompatibilityReason.VIDEO_CODEC
        }
        if (video.profile.normalized() !in ALLOWED_VIDEO_PROFILES) {
            reasons += CarMediaIncompatibilityReason.VIDEO_PROFILE
        }

        val level = video.level.toPositiveInt()
        if (level == null || level > profile.maxVideoLevel) {
            reasons += CarMediaIncompatibilityReason.VIDEO_LEVEL
        }

        val width = video.width.toPositiveInt()
        val height = video.height.toPositiveInt()
        if (width == null || height == null ||
            width > profile.maxWidth || height > profile.maxHeight
        ) {
            reasons += CarMediaIncompatibilityReason.VIDEO_DIMENSIONS
        }
        if (width == null || height == null || width % 2 != 0 || height % 2 != 0) {
            reasons += CarMediaIncompatibilityReason.VIDEO_DIMENSIONS_ODD
        }
        if (video.pixelFormat.normalized() != profile.pixelFormat) {
            reasons += CarMediaIncompatibilityReason.VIDEO_PIXEL_FORMAT
        }
        if (video.fieldOrder.normalized() != "progressive") {
            reasons += CarMediaIncompatibilityReason.VIDEO_FIELD_ORDER
        }
        if (video.rotations.any { it.toDecimalOrNull()?.compareTo(BigDecimal.ZERO) != 0 }) {
            reasons += CarMediaIncompatibilityReason.VIDEO_ROTATION
        }

        val sampleAspectRatio = ExactRational.parse(video.sampleAspectRatio)
        if (sampleAspectRatio != ExactRational.ONE) {
            reasons += CarMediaIncompatibilityReason.VIDEO_SAMPLE_ASPECT_RATIO
        }
        val displayAspectRatio = ExactRational.parse(video.displayAspectRatio)
        val expectedDisplayAspectRatio = if (width != null && height != null) {
            ExactRational.of(width.toLong(), height.toLong())
        } else {
            null
        }
        if (displayAspectRatio == null ||
            displayAspectRatio <= ExactRational.ZERO ||
            displayAspectRatio != expectedDisplayAspectRatio
        ) {
            reasons += CarMediaIncompatibilityReason.VIDEO_DISPLAY_ASPECT_RATIO
        }
        if (video.attachedPicture.toNonNegativeInt() != 0) {
            reasons += CarMediaIncompatibilityReason.VIDEO_ATTACHED_PICTURE
        }

        val videoBitrate = video.bitRate.toPositiveLong()
        if (videoBitrate == null || videoBitrate > profile.maxVideoBitrate) {
            reasons += CarMediaIncompatibilityReason.VIDEO_BITRATE
        }

        val averageFrameRate = ExactRational.parse(video.averageFrameRate)
        val realFrameRate = ExactRational.parse(video.realFrameRate)
        if (averageFrameRate == null || realFrameRate == null ||
            averageFrameRate <= ExactRational.ZERO ||
            realFrameRate <= ExactRational.ZERO ||
            averageFrameRate !in ALLOWED_FRAME_RATES ||
            realFrameRate !in ALLOWED_FRAME_RATES
        ) {
            reasons += CarMediaIncompatibilityReason.VIDEO_FRAME_RATE
        }
        if (averageFrameRate == null || realFrameRate == null ||
            averageFrameRate != realFrameRate
        ) {
            reasons += CarMediaIncompatibilityReason.VIDEO_VARIABLE_FRAME_RATE
        }
    }

    private fun validateAudio(
        audio: CarMediaStreamMetadata,
        profile: VideoCompatibilityProfile,
        reasons: MutableSet<CarMediaIncompatibilityReason>
    ) {
        if (audio.codecName.normalized() != profile.audioCodec) {
            reasons += CarMediaIncompatibilityReason.AUDIO_CODEC
        }
        if (audio.profile.normalized() !in ALLOWED_AUDIO_PROFILES) {
            reasons += CarMediaIncompatibilityReason.AUDIO_PROFILE
        }
        if (audio.sampleRate.toPositiveInt() !in ALLOWED_AUDIO_SAMPLE_RATES) {
            reasons += CarMediaIncompatibilityReason.AUDIO_SAMPLE_RATE
        }

        val channels = audio.channels.toPositiveInt()
        if (channels !in setOf(1, 2)) {
            reasons += CarMediaIncompatibilityReason.AUDIO_CHANNELS
        }
        val channelLayout = audio.channelLayout.normalized().takeIf { it.isNotBlank() }
        if (channelLayout != null) {
            val expectedLayout = when (channels) {
                1 -> "mono"
                2 -> "stereo"
                else -> null
            }
            if (channelLayout != expectedLayout) {
                reasons += CarMediaIncompatibilityReason.AUDIO_CHANNEL_LAYOUT
            }
        }

        val audioBitrate = audio.bitRate.toPositiveLong()
        if (audioBitrate == null ||
            audioBitrate > profile.audioCompatibilityBitrateCeiling
        ) {
            reasons += CarMediaIncompatibilityReason.AUDIO_BITRATE
        }
    }

    private fun String?.normalized(): String {
        return this
            ?.trim()
            ?.lowercase(Locale.US)
            ?.replace(Regex("[_-]+"), " ")
            ?.replace(Regex("\\s+"), " ")
            .orEmpty()
    }

    private fun String?.toPositiveInt(): Int? {
        return this?.trim()?.toIntOrNull()?.takeIf { it > 0 }
    }

    private fun String?.toNonNegativeInt(): Int? {
        return this?.trim()?.toIntOrNull()?.takeIf { it >= 0 }
    }

    private fun String?.toPositiveLong(): Long? {
        return this?.trim()?.toLongOrNull()?.takeIf { it > 0L }
    }

    private fun String?.toPositiveDecimal(): BigDecimal? {
        return toDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
    }

    private fun String?.toDecimalOrNull(): BigDecimal? {
        return this?.trim()?.takeIf(String::isNotEmpty)?.let {
            runCatching { BigDecimal(it) }.getOrNull()
        }
    }

    private val ALLOWED_VIDEO_PROFILES = setOf("baseline", "constrained baseline")
    private val ALLOWED_AUDIO_PROFILES = setOf("lc", "aac lc", "low complexity")
    private val ALLOWED_AUDIO_SAMPLE_RATES = setOf(44_100, 48_000)
    private val ALLOWED_FRAME_RATES = setOf(
        ExactRational.of(24, 1),
        ExactRational.of(25, 1),
        ExactRational.of(30_000, 1_001),
        ExactRational.of(2_997, 100),
        ExactRational.of(30, 1)
    )
}

private class ExactRational private constructor(
    val numerator: BigInteger,
    val denominator: BigInteger
) : Comparable<ExactRational> {
    override fun compareTo(other: ExactRational): Int {
        return (numerator * other.denominator).compareTo(other.numerator * denominator)
    }

    override fun equals(other: Any?): Boolean {
        return other is ExactRational &&
            numerator == other.numerator &&
            denominator == other.denominator
    }

    override fun hashCode(): Int {
        return 31 * numerator.hashCode() + denominator.hashCode()
    }

    companion object {
        val ZERO = of(0, 1)
        val ONE = of(1, 1)

        fun of(numerator: Long, denominator: Long): ExactRational {
            require(denominator != 0L)
            return create(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator))
                ?: error("Non-zero denominator must produce a rational.")
        }

        fun parse(rawValue: String?): ExactRational? {
            val value = rawValue?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val separator = when {
                '/' in value -> '/'
                ':' in value -> ':'
                else -> null
            }
            return if (separator != null) {
                val parts = value.split(separator)
                if (parts.size != 2) return null
                val numerator = parts[0].trim().toBigIntegerOrNull() ?: return null
                val denominator = parts[1].trim().toBigIntegerOrNull() ?: return null
                create(numerator, denominator)
            } else {
                val decimal = runCatching { BigDecimal(value) }.getOrNull() ?: return null
                val normalized = decimal.stripTrailingZeros()
                val numerator = normalized.unscaledValue()
                val denominator = if (normalized.scale() <= 0) {
                    BigInteger.ONE
                } else {
                    BigInteger.TEN.pow(normalized.scale())
                }
                create(numerator, denominator)
            }
        }

        private fun create(
            numerator: BigInteger,
            denominator: BigInteger
        ): ExactRational? {
            if (denominator == BigInteger.ZERO) return null
            val normalizedNumerator = if (denominator.signum() < 0) -numerator else numerator
            val normalizedDenominator = denominator.abs()
            val divisor = normalizedNumerator.abs().gcd(normalizedDenominator)
                .takeUnless { it == BigInteger.ZERO }
                ?: BigInteger.ONE
            return ExactRational(
                numerator = normalizedNumerator / divisor,
                denominator = normalizedDenominator / divisor
            )
        }
    }
}
