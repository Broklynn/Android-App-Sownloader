package com.androiddownload.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerMediaKindMapperTest {
    @Test
    fun `maps music category to audio media kind`() {
        assertEquals(
            PlayerMediaKind.AUDIO,
            PlayerMediaKindMapper.fromCategory(PlayerCategory.MUSIC)
        )
    }

    @Test
    fun `maps video category to video media kind`() {
        assertEquals(
            PlayerMediaKind.VIDEO,
            PlayerMediaKindMapper.fromCategory(PlayerCategory.VIDEO)
        )
    }
}
