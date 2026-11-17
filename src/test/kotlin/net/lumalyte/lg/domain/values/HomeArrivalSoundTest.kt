package net.lumalyte.lg.domain.values

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class HomeArrivalSoundTest {
    @Test
    fun `home arrival is the studio's Quiet portal recipe`() {
        assertEquals(listOf(
            SoundCue("entity.enderman.teleport", 0.13f, 1.3f, 0),
            SoundCue("block.note_block.harp", 0.23f, 1.1f, 5),
        ), HomeArrivalSound.cues)
    }

    @Test
    fun `staff can audition it`() {
        assertEquals(HomeArrivalSound.cues, assertNotNull(GuildSoundCatalog.find("home-teleport")).cues)
    }
}
