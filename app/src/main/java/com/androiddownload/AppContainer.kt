package com.androiddownload

import android.content.Context
import androidx.room.Room
import com.androiddownload.core.database.AppDatabase
import com.androiddownload.download.data.DownloadRepository
import com.androiddownload.download.http.HttpDownloader
import com.androiddownload.download.maintenance.DownloadStartupMaintenance
import com.androiddownload.download.notification.DownloadNotifier
import com.androiddownload.download.queue.DownloadQueue
import com.androiddownload.download.ytdlp.YtDlpDownloader
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "android-download.db"
    ).addMigrations(AppDatabase.MIGRATION_1_2).build()

    val repository: DownloadRepository = DownloadRepository(database.downloadDao())

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val notifier: DownloadNotifier = DownloadNotifier(appContext)

    val downloader: HttpDownloader = HttpDownloader(
        context = appContext,
        client = okHttpClient,
        repository = repository
    )

    val ytDlpDownloader: YtDlpDownloader = YtDlpDownloader(
        context = appContext,
        repository = repository
    )

    val queue: DownloadQueue = DownloadQueue(repository)

    val downloadStartupMaintenance: DownloadStartupMaintenance = DownloadStartupMaintenance(
        context = appContext,
        repository = repository
    )
}
