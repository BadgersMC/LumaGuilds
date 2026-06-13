package net.lumalyte.lg.api

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.services.BankService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.entities.RankPermission
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GuildLookupImplTest {

    private val guildId = UUID.randomUUID()
    private val playerId = UUID.randomUUID()
    private val guilds = mockk<GuildService>()
    private val members = mockk<MemberService>()
    private val ranks = mockk<RankService>()
    private val banks = mockk<BankService>()
    private val lookup = GuildLookupImpl(guilds, members, ranks, banks)

    @Test
    fun `getGuild maps entity to summary`() {
        every { guilds.getGuild(guildId) } returns Guild(
            id = guildId, name = "TestGuild", createdAt = Instant.now(), tag = "&aTAG", emoji = ":x:",
        )
        val summary = lookup.getGuild(guildId)
        assertEquals(GuildSummary(guildId, "TestGuild", "&aTAG", ":x:"), summary)
    }

    @Test
    fun `getGuild returns null when absent`() {
        every { guilds.getGuild(guildId) } returns null
        assertNull(lookup.getGuild(guildId))
    }

    @Test
    fun `getPlayerGuildIds delegates to member service`() {
        every { members.getPlayerGuilds(playerId) } returns setOf(guildId)
        assertEquals(setOf(guildId), lookup.getPlayerGuildIds(playerId))
    }

    @Test
    fun `isMember true when member exists`() {
        every { members.getMember(playerId, guildId) } returns Member(
            playerId = playerId, guildId = guildId, rankId = UUID.randomUUID(), joinedAt = Instant.now(),
        )
        assertTrue(lookup.isMember(playerId, guildId))
    }

    @Test
    fun `isMember false when member absent`() {
        every { members.getMember(playerId, guildId) } returns null
        assertFalse(lookup.isMember(playerId, guildId))
    }

    @Test
    fun `hasShopPermission maps enum name and delegates`() {
        every { members.hasPermission(playerId, guildId, RankPermission.EDIT_SHOP_STOCK) } returns true
        assertTrue(lookup.hasShopPermission(playerId, guildId, "EDIT_SHOP_STOCK"))
    }

    @Test
    fun `hasShopPermission returns false for unknown permission name`() {
        assertFalse(lookup.hasShopPermission(playerId, guildId, "NOT_A_PERMISSION"))
    }

    @Test
    fun `hasShopPermission returns false when service throws`() {
        every {
            members.hasPermission(playerId, guildId, RankPermission.EDIT_SHOP_STOCK)
        } throws RuntimeException("db down")
        assertFalse(lookup.hasShopPermission(playerId, guildId, "EDIT_SHOP_STOCK"))
    }

    @Test
    fun `hasRankAtLeast true when player priority not below target`() {
        val playerRankId = UUID.randomUUID()
        val officerRankId = UUID.randomUUID()
        every { members.getPlayerRankId(playerId, guildId) } returns playerRankId
        every { ranks.listRanks(guildId) } returns setOf(
            Rank(id = playerRankId, guildId = guildId, name = "Owner", priority = 0),
            Rank(id = officerRankId, guildId = guildId, name = "Officer", priority = 2),
        )
        assertTrue(lookup.hasRankAtLeast(playerId, guildId, "officer"))
    }

    @Test
    fun `hasRankAtLeast false when player rank is lower authority than target`() {
        val playerRankId = UUID.randomUUID()
        val officerRankId = UUID.randomUUID()
        every { members.getPlayerRankId(playerId, guildId) } returns playerRankId
        every { ranks.listRanks(guildId) } returns setOf(
            Rank(id = playerRankId, guildId = guildId, name = "Member", priority = 3),
            Rank(id = officerRankId, guildId = guildId, name = "Officer", priority = 2),
        )
        assertFalse(lookup.hasRankAtLeast(playerId, guildId, "Officer"))
    }

    @Test
    fun `hasRankAtLeast false when target rank not found`() {
        val playerRankId = UUID.randomUUID()
        every { members.getPlayerRankId(playerId, guildId) } returns playerRankId
        every { ranks.listRanks(guildId) } returns setOf(
            Rank(id = playerRankId, guildId = guildId, name = "Member", priority = 3),
        )
        assertFalse(lookup.hasRankAtLeast(playerId, guildId, "Officer"))
    }

    @Test
    fun `hasRankAtLeast false when player has no rank`() {
        every { members.getPlayerRankId(playerId, guildId) } returns null
        assertFalse(lookup.hasRankAtLeast(playerId, guildId, "Officer"))
    }

    @Test
    fun `getBankBalance delegates and widens to Long`() {
        every { banks.getBalance(guildId) } returns 4200
        assertEquals(4200L, lookup.getBankBalance(guildId))
    }

    @Test
    fun `bankWithdraw translates non-null result to true`() {
        every { banks.withdraw(guildId, playerId, 100, "r") } returns mockk()
        assertTrue(lookup.bankWithdraw(guildId, playerId, 100, "r"))
    }

    @Test
    fun `bankDeposit translates null result to false`() {
        every { banks.deposit(guildId, playerId, 50, "r") } returns null
        assertFalse(lookup.bankDeposit(guildId, playerId, 50, "r"))
    }

    @Test
    fun `bank amounts beyond Int range are rejected without hitting the service`() {
        assertFalse(lookup.bankWithdraw(guildId, playerId, Int.MAX_VALUE.toLong() + 1, "r"))
        assertFalse(lookup.bankDeposit(guildId, playerId, 0, "r"))
    }
}
