package net.lumalyte.lg.domain.values

import net.lumalyte.lg.domain.entities.GuildProgression
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class RemovedCosmeticPerksTest {
    @Test
    fun `new guilds do not receive cosmetic progression perks`() {
        assertTrue(GuildProgression.create(UUID.randomUUID(), 1000).unlockedPerks.isEmpty())
    }

    @Test
    fun `retired cosmetic names cannot resolve as active perks`() {
        for (name in listOf("CUSTOM_BANNER_COLORS", "ANIMATED_EMOJIS")) {
            assertTrue(runCatching { PerkType.valueOf(name) }.isFailure)
        }
    }
}
