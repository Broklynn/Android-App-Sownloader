package com.androiddownload.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        assertEquals(720, profile.maxHeight)
        assertEquals(30, profile.maxFps)
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
