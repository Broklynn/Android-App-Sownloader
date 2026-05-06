package com.androiddownload

import android.app.Application
import com.androiddownload.core.utils.YtDlpDiagnostics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AndroidDownloadApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        val startedAt = System.currentTimeMillis()
        val appContainer = container
        appScope.launch {
            recordStartup("startup", "iniciado", startedAt)
        }

        appScope.launch {
            val maintenanceStartedAt = System.currentTimeMillis()
            recordStartup("manutencao", "agendada em background", maintenanceStartedAt)
            runCatching {
                appContainer.downloadStartupMaintenance.recoverAndClean()
            }.onSuccess {
                recordStartup("manutencao", "finalizada", maintenanceStartedAt)
            }.onFailure { exception ->
                recordStartup("manutencao", "falha", maintenanceStartedAt, exception.message)
            }
        }

        appScope.launch {
            val initStartedAt = System.currentTimeMillis()
            recordStartup("yt-dlp init", "agendado em background", initStartedAt)
            runCatching {
                appContainer.ytDlpDownloader.initialize()
            }.onSuccess {
                recordStartup("yt-dlp init", "finalizado", initStartedAt)
            }.onFailure { exception ->
                recordStartup("yt-dlp init", "falha", initStartedAt, exception.message)
            }
        }
    }

    override fun onTerminate() {
        appScope.cancel()
        super.onTerminate()
    }

    private fun recordStartup(
        attempt: String,
        result: String,
        startedAt: Long,
        error: String? = null
    ) {
        YtDlpDiagnostics.record(
            context = this,
            url = "app",
            option = "startup",
            attempt = attempt,
            result = result,
            error = error,
            durationMs = System.currentTimeMillis() - startedAt,
            type = "startup"
        )
    }
}
