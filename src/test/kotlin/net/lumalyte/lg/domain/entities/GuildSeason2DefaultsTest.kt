package net.lumalyte.lg.domain.entities

import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFalse

class GuildSeason2DefaultsTest {
    @Test
    fun `lunar tracking is opt in for new guilds`() {
        val guild = Guild(UUID.randomUUID(), "Season Two", createdAt = Instant.EPOCH)
        assertFalse(guild.trackingEnabled)
    }
}