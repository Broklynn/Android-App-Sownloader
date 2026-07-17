package com.androiddownload.download.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpResumePlannerTest {
    @Test
    fun missingPartStartsFromZero() {
        assertEquals(
            0L,
            resolveOffset(persistedBytes = 200L, tempFileExists = false, tempFileLength = 0L)
        )
    }

    @Test
    fun emptyPartStartsFromZero() {
        assertEquals(
            0L,
            resolveOffset(persistedBytes = 200L, tempFileExists = true, tempFileLength = 0L)
        )
    }

    @Test
    fun matchingRoomAndPartUsePartLength() {
        assertEquals(
            300L,
            resolveOffset(persistedBytes = 300L, tempFileExists = true, tempFileLength = 300L)
        )
    }

    @Test
    fun roomBehindPartUsesPartLength() {
        assertEquals(
            400L,
            resolveOffset(persistedBytes = 300L, tempFileExists = true, tempFileLength = 400L)
        )
    }

    @Test
    fun roomAheadOfPartUsesPartLength() {
        assertEquals(
            300L,
            resolveOffset(persistedBytes = 400L, tempFileExists = true, tempFileLength = 300L)
        )
    }

    @Test
    fun invalidPersistedOffsetIsNormalizedWithoutOverridingPartLength() {
        val resolution = HttpResumePlanner.resolveOffset(
            persistedDownloadedBytes = -100L,
            tempFileExists = true,
            tempFileLength = 300L,
            resumeAllowed = true
        )

        assertEquals(0L, resolution.persistedOffset)
        assertEquals(300L, resolution.requestedOffset)
        assertTrue(resolution.reconciled)
    }

    @Test
    fun matchingPartialContentAllowsAppend() {
        val plan = HttpResumePlanner.planResponse(
            requestedOffset = 400L,
            responseCode = 206,
            contentRange = "bytes 400-999/1000",
            canRestartFromZero = true
        )

        assertTrue(plan is HttpResumePlanner.ResponsePlan.Append)
    }

    @Test
    fun mismatchedPartialContentRefusesAppend() {
        val plan = HttpResumePlanner.planResponse(
            requestedOffset = 400L,
            responseCode = 206,
            contentRange = "bytes 300-999/1000",
            canRestartFromZero = true
        )

        assertFalse(plan is HttpResumePlanner.ResponsePlan.Append)
        assertTrue(plan is HttpResumePlanner.ResponsePlan.RestartFromZero)
    }

    @Test
    fun partialContentWithUnexpectedBodyLengthRefusesAppend() {
        val plan = HttpResumePlanner.planResponse(
            requestedOffset = 400L,
            responseCode = 206,
            contentRange = "bytes 400-999/1000",
            canRestartFromZero = true,
            responseBodyLength = 500L
        )

        assertTrue(plan is HttpResumePlanner.ResponsePlan.RestartFromZero)
    }

    @Test
    fun changedRemoteTotalRequestsControlledRestart() {
        val plan = HttpResumePlanner.planResponse(
            requestedOffset = 400L,
            responseCode = 206,
            contentRange = "bytes 400-1199/1200",
            canRestartFromZero = true,
            responseBodyLength = 800L,
            expectedTotalBytes = 1_000L
        )

        assertTrue(plan is HttpResumePlanner.ResponsePlan.RestartFromZero)
    }

    @Test
    fun knownExpectedTotalRejectsUnknownContentRangeTotal() {
        val plan = HttpResumePlanner.planResponse(
            requestedOffset = 400L,
            responseCode = 206,
            contentRange = "bytes 400-999/*",
            canRestartFromZero = true,
            responseBodyLength = 600L,
            expectedTotalBytes = 1_000L
        )

        assertTrue(plan is HttpResumePlanner.ResponsePlan.RestartFromZero)
    }

    @Test
    fun unknownExpectedTotalAllowsUnknownContentRangeTotal() {
        val plan = HttpResumePlanner.planResponse(
            requestedOffset = 400L,
            responseCode = 206,
            contentRange = "bytes 400-999/*",
            canRestartFromZero = true,
            responseBodyLength = 600L,
            expectedTotalBytes = 0L
        )

        assertTrue(plan is HttpResumePlanner.ResponsePlan.Append)
    }

    @Test
    fun malformedOrMissingContentRangeRefusesAppend() {
        listOf(null, "", "bytes invalid", "bytes 400-399/1000", "bytes 400-1000/1000")
            .forEach { contentRange ->
                val plan = HttpResumePlanner.planResponse(
                    requestedOffset = 400L,
                    responseCode = 206,
                    contentRange = contentRange,
                    canRestartFromZero = true
                )

                assertFalse(plan is HttpResumePlanner.ResponsePlan.Append)
            }
    }

    @Test
    fun rangeNotSatisfiableRequestsControlledRestart() {
        val plan = HttpResumePlanner.planResponse(
            requestedOffset = 1_100L,
            responseCode = 416,
            contentRange = "bytes */1000",
            canRestartFromZero = true
        )

        assertTrue(plan is HttpResumePlanner.ResponsePlan.RestartFromZero)
    }

    private fun resolveOffset(
        persistedBytes: Long,
        tempFileExists: Boolean,
        tempFileLength: Long
    ): Long {
        return HttpResumePlanner.resolveOffset(
            persistedDownloadedBytes = persistedBytes,
            tempFileExists = tempFileExists,
            tempFileLength = tempFileLength,
            resumeAllowed = true
        ).requestedOffset
    }

}
