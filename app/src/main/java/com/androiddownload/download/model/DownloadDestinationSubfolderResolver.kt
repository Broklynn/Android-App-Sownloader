package com.androiddownload.download.model

import com.androiddownload.core.model.DownloadEntity

object DownloadDestinationSubfolderResolver {
    const val YOUTUBE = "Youtube"
    const val INSTAGRAM = "Instagram"
    const val TIKTOK = "TikTok"
    const val FILES = "Arquivos"

    fun resolve(download: DownloadEntity): String {
        return when (DownloadOriginResolver.resolve(download)) {
            DownloadOrigin.YOUTUBE -> YOUTUBE
            DownloadOrigin.INSTAGRAM -> INSTAGRAM
            DownloadOrigin.TIKTOK -> TIKTOK
            DownloadOrigin.FILES,
            DownloadOrigin.UNKNOWN -> FILES
        }
    }
}
