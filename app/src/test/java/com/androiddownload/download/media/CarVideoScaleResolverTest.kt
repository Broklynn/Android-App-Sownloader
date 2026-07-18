package com.androiddownload.download.media

import com.androiddownload.core.utils.VideoCompatibilityProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CarVideoScaleResolverTest {
    private val profile = VideoCompatibilityProfile.CAR_COMPATIBLE_720P

    @Test
    fun fullHdLandscapeFits1280By720() {
        assertEquals(
            CarVideoScaleResolver.VideoSize(1280, 720),
            resolve(1920, 1080)
        )
    }

    @Test
    fun portraitIsLimitedByHeight() {
        assertEquals(
            CarVideoScaleResolver.VideoSize(406, 720),
            resolve(1080, 1920)
        )
    }

    @Test
    fun fourByThreeFitsAt960By720() {
        assertEquals(
            CarVideoScaleResolver.VideoSize(960, 720),
            resolve(1440, 1080)
        )
    }

    @Test
    fun squareFitsAt720By720() {
        assertEquals(
            CarVideoScaleResolver.VideoSize(720, 720),
            resolve(1080, 1080)
        )
    }

    @Test
    fun smallVideoIsNotUpscaled() {
        assertEquals(
            CarVideoScaleResolver.VideoSize(640, 360),
            resolve(640, 360)
        )
    }

    @Test
    fun oddDimensionsProduceEvenOutputWithoutUpscaling() {
        assertEquals(
            CarVideoScaleResolver.VideoSize(640, 358),
            resolve(641, 359)
        )
    }

    @Test
    fun allRequiredGeometriesStayInsideBoxAndPreserveAspectRatio() {
        listOf(
            1920 to 1080,
            1080 to 1920,
            1440 to 1080,
            1080 to 1080,
            640 to 360,
            641 to 359
        ).forEach { (inputWidth, inputHeight) ->
            val output = resolve(inputWidth, inputHeight)

            assertTrue(output.width <= profile.maxWidth)
            assertTrue(output.height <= profile.maxHeight)
            assertTrue(output.width <= inputWidth)
            assertTrue(output.height <= inputHeight)
            assertEquals(0, output.width % 2)
            assertEquals(0, output.height % 2)
            assertAspectRatioPreserved(inputWidth, inputHeight, output)
        }
    }

    private fun resolve(inputWidth: Int, inputHeight: Int): CarVideoScaleResolver.VideoSize {
        return CarVideoScaleResolver.resolve(inputWidth, inputHeight, profile)
    }

    private fun assertAspectRatioPreserved(
        inputWidth: Int,
        inputHeight: Int,
        output: CarVideoScaleResolver.VideoSize
    ) {
        val inputRatio = inputWidth.toDouble() / inputHeight
        val outputRatio = output.width.toDouble() / output.height
        val relativeDifference = abs(outputRatio - inputRatio) / inputRatio
        assertTrue(
            "Aspect ratio changed from $inputRatio to $outputRatio",
            relativeDifference < 0.01
        )
    }
}
