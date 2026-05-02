package com.androiddownload

import android.app.Application
import kotlinx.coroutines.runBlocking

class AndroidDownloadApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        Thread {
            runCatching {
                runBlocking {
                    container.downloadStartupMaintenance.recoverAndClean()
                }
                container.ytDlpDownloader.initialize()
            }
        }.start()
    }
}
