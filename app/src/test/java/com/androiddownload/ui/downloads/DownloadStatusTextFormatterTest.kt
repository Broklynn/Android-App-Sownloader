package com.androiddownload.ui.downloads

import com.androiddownload.core.model.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadStatusTextFormatterTest {
    @Test
    fun statusLabelReturnsQueuedText() {
        assertEquals("Na fila", statusLabel(DownloadStatus.QUEUED))
    }

    @Test
    fun statusLabelReturnsPreparingText() {
        assertEquals("Preparando", statusLabel(DownloadStatus.PREPARING))
    }

    @Test
    fun statusLabelReturnsRunningText() {
        assertEquals("Baixando", statusLabel(DownloadStatus.RUNNING))
    }

    @Test
    fun statusLabelReturnsPausedText() {
        assertEquals("Pausado", statusLabel(DownloadStatus.PAUSED))
    }

    @Test
    fun statusLabelReturnsCompletedText() {
        assertEquals("Concluido", statusLabel(DownloadStatus.COMPLETED))
    }

    @Test
    fun statusLabelReturnsFailedText() {
        assertEquals("Falhou", statusLabel(DownloadStatus.FAILED))
    }

    @Test
    fun statusLabelReturnsCanceledText() {
        assertEquals("Cancelado", statusLabel(DownloadStatus.CANCELED))
    }

    @Test
    fun progressLabelForQueuedPreservesQueuedText() {
        assertEquals("Na fila", progressLabel(DownloadStatus.QUEUED, progress = 25))
    }

    @Test
    fun progressLabelForPreparingPreservesPreparingProgressText() {
        assertEquals("Preparando...", progressLabel(DownloadStatus.PREPARING, progress = 25))
    }

    @Test
    fun progressLabelForIndeterminateRunningPreservesUnknownText() {
        assertEquals("Baixando...", progressLabel(DownloadStatus.RUNNING, progress = 25, indeterminate = true))
    }

    @Test
    fun progressLabelForDeterminateRunningPreservesProgressAndUnknownText() {
        assertEquals("25% Baixando...", progressLabel(DownloadStatus.RUNNING, progress = 25))
    }

    @Test
    fun progressLabelForPausedWithZeroProgressPreservesPausedText() {
        assertEquals("Pausado", progressLabel(DownloadStatus.PAUSED, progress = 0))
    }

    @Test
    fun progressLabelForPausedWithPositiveProgressPreservesProgressAndPausedText() {
        assertEquals("25% Pausado", progressLabel(DownloadStatus.PAUSED, progress = 25))
    }

    @Test
    fun progressLabelForCompletedReturnsOneHundredPercentCompleted() {
        assertEquals("100% Concluido", progressLabel(DownloadStatus.COMPLETED, progress = 25))
    }

    @Test
    fun progressLabelForFailedPreservesFailedText() {
        assertEquals("Falhou", progressLabel(DownloadStatus.FAILED, progress = 25))
    }

    @Test
    fun progressLabelForCanceledPreservesCanceledText() {
        assertEquals("Cancelado", progressLabel(DownloadStatus.CANCELED, progress = 25))
    }

    @Test
    fun progressLabelUsesReceivedProgressWithoutNormalizing() {
        assertEquals("125% Baixando...", progressLabel(DownloadStatus.RUNNING, progress = 125))
    }

    private fun statusLabel(status: DownloadStatus): String {
        return DownloadStatusTextFormatter.statusLabel(status, labels())
    }

    private fun progressLabel(
        status: DownloadStatus,
        progress: Int,
        indeterminate: Boolean = false
    ): String {
        return DownloadStatusTextFormatter.progressLabel(
            status = status,
            progress = progress,
            indeterminate = indeterminate,
            labels = labels()
        )
    }

    private fun labels(): DownloadStatusTextLabels {
        return DownloadStatusTextLabels(
            queued = "Na fila",
            preparing = "Preparando",
            preparingProgress = "Preparando...",
            running = "Baixando",
            paused = "Pausado",
            completed = "Concluido",
            failed = "Falhou",
            canceled = "Cancelado",
            downloadingUnknown = "Baixando..."
        )
    }
}
