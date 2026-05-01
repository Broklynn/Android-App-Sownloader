package com.androiddownload.download.queue

import com.androiddownload.download.data.DownloadRepository

class DownloadQueue(
    private val repository: DownloadRepository
) {
    suspend fun enqueue(sourceUrl: String): Long {
        return repository.enqueue(sourceUrl)
    }
}
