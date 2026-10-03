package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildChatRankSettingsRepository
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.RankPermission
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class GuildChatRankSettingsServiceTest {
    @Test
    fun settingRequiresGuildManagementPermission() {
        val guildId = UUID.randomUUID()
        val actor = UUID.randomUUID()
        val repository = mockk<GuildChatRankSettingsRepository>()
        val guilds = mockk<GuildService>()
        every { guilds.getGuild(guildId) } returns Guild(guildId, "Badgers", createdAt = Instant.EPOCH)
        every { guilds.hasPermission(actor, guildId, RankPermission.MANAGE_GUILD_SETTINGS) } returns false
        val service = GuildChatRankSettingsService(repository, guilds)
        assertFalse(service.setRanksVisible(guildId, false, actor))
        verify(exactly = 0) { repository.setRanksVisible(any(), any()) }

        every { guilds.hasPermission(actor, guildId, RankPermission.MANAGE_GUILD_SETTINGS) } returns true
        every { repository.setRanksVisible(guildId, false) } returns true
        assertTrue(service.setRanksVisible(guildId, false, actor))
        every { repository.setRanksVisible(guildId, true) } returns false
        assertFalse(service.setRanksVisible(guildId, true, actor))
    }
}
