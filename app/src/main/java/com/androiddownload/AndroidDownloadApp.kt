package com.androiddownload

import android.app.Application

class AndroidDownloadApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        Thread {
            runCatching {
                container.ytDlpDownloader.initialize()
            }
        }.start()
    }
}
