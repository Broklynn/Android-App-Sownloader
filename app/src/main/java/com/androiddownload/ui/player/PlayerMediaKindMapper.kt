package com.androiddownload.ui.player

object PlayerMediaKindMapper {
    fun fromCategory(category: PlayerCategory): PlayerMediaKind =
        when (category) {
            PlayerCategory.MUSIC -> PlayerMediaKind.AUDIO
            PlayerCategory.VIDEO -> PlayerMediaKind.VIDEO
        }
}
