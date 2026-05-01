package com.androiddownload.core.database

import androidx.room.TypeConverter
import com.androiddownload.core.model.DownloadStatus

class DownloadStatusConverter {
    @TypeConverter
    fun fromStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toStatus(value: String): DownloadStatus = DownloadStatus.valueOf(value)
}
