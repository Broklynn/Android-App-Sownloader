package com.androiddownload

import android.app.Application

class AndroidDownloadApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
