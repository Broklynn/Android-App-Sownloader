package com.androiddownload.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoCompatibilityProfileTest {
    @Test
    fun normalSelectorDoesNotEnableCompatibility() {
        assertFalse(VideoCompatibilityProfile.isEnabled(YtDlpQualityOptions.SELECTOR_MP4_720P))
        assertNull(VideoCompatibilityProfile.fromSelector(YtDlpQualityOptions.SELECTOR_MP4_720P))
    }

    @Test
    fun carCompatible720pSelectorEnablesCompatibility() {
        assertTrue(VideoCompatibilityProfile.isEnabled(VideoCompatibilityProfile.SELECTOR_CAR_COMPATIBLE_720P))
        assertEquals(
            VideoCompatibilityProfile.CAR_COMPATIBLE_720P,
            VideoCompatibilityProfile.fromSelector(VideoCompatibilityProfile.SELECTOR_CAR_COMPATIBLE_720P)
        )
    }

    @Test
    fun carCompatible720pProfileUsesExpectedLimits() {
        val profile = VideoCompatibilityProfile.CAR_COMPATIBLE_720P

        assertEquals(1280, profile.maxWidth)
        assertEquals(720, profile.maxHeight)
        assertEquals(30, profile.maxFps)
        assertEquals(31, profile.maxVideoLevel)
        assertEquals(10_000_000L, profile.maxVideoBitrate)
        assertEquals(192_000L, profile.audioEncodingTargetBitrate)
        assertEquals(200_000L, profile.audioCompatibilityBitrateCeiling)
        assertEquals(
            VideoCompatibilityProfile.CAR_AUDIO_ENCODING_TARGET_BITRATE,
            profile.audioEncodingTargetBitrate
        )
        assertEquals(
            VideoCompatibilityProfile.CAR_AUDIO_COMPATIBILITY_BITRATE_CEILING,
            profile.audioCompatibilityBitrateCeiling
        )
        assertNotEquals(
            profile.audioEncodingTargetBitrate,
            profile.audioCompatibilityBitrateCeiling
        )
    }

    @Test
    fun carCompatible720pProfileUsesExpectedOutputFormats() {
        val profile = VideoCompatibilityProfile.CAR_COMPATIBLE_720P

        assertEquals("mp4", profile.container)
        assertEquals("h264", profile.videoCodec)
        assertEquals("aac", profile.audioCodec)
        assertEquals("yuv420p", profile.pixelFormat)
    }

    @Test
    fun selectorMatchingIgnoresOuterWhitespace() {
        assertTrue(
            VideoCompatibilityProfile.isEnabled("  ${VideoCompatibilityProfile.SELECTOR_CAR_COMPATIBLE_720P}  ")
        )
    }
}
