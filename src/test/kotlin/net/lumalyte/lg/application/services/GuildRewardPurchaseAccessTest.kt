package net.lumalyte.lg.application.services

import io.mockk.*
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.domain.entities.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class GuildRewardPurchaseAccessTest {
    @Test fun `membership both permissions and guild rank identity are required on every check`() {
        val actor = UUID.randomUUID()
        val guild = UUID.randomUUID()
        val rankId = UUID.randomUUID()
        val members = mockk<MemberRepository>()
        val ranks = mockk<RankRepository>()
        val access = GuildRewardPurchaseAccess(members, ranks)
        every { members.getByPlayerAndGuild(actor, guild) } returns null
        assertFalse(access.allowed(actor, guild))
        every { members.getByPlayerAndGuild(actor, guild) } returns Member(actor, guild, rankId, Instant.EPOCH)
        val complete = Rank(rankId, guild, "Leader", permissions = setOf(RankPermission.MANAGE_GUILD_SETTINGS, RankPermission.WITHDRAW_FROM_BANK))
        every { ranks.getById(rankId) } returns complete
        assertTrue(access.allowed(actor, guild))
        for (permission in complete.permissions) {
            every { ranks.getById(rankId) } returns complete.copy(permissions = setOf(permission))
            assertFalse(access.allowed(actor, guild))
        }
        every { ranks.getById(rankId) } returns complete.copy(guildId = UUID.randomUUID())
        assertFalse(access.allowed(actor, guild))
        every { ranks.getById(rankId) } returns null
        assertFalse(access.allowed(actor, guild))
    }
}
