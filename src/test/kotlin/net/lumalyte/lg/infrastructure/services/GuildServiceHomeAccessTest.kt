package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.application.persistence.RelationRepository
import net.lumalyte.lg.application.services.GuildVaultService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.domain.values.Position3D
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class GuildServiceHomeAccessTest {

    private val guildId = UUID.randomUUID()
    private val ownerRankId = UUID.randomUUID()
    private val memberRankId = UUID.randomUUID()
    private val ownerPlayerId = UUID.randomUUID()
    private val memberPlayerId = UUID.randomUUID()
    private val homeName = "main"
    private val worldId = UUID.randomUUID()

    private lateinit var guildRepository: GuildRepository
    private lateinit var rankRepository: RankRepository
    private lateinit var memberRepository: MemberRepository
    private lateinit var service: GuildServiceBukkit

    private fun homeWith(allowed: Set<UUID>) = GuildHome(worldId, Position3D(0, 64, 0), allowed)

    private fun setup(homeAllowed: Set<UUID>) {
        val ownerRank = Rank(ownerRankId, guildId, "Owner", 0, RankPermission.values().toSet())
        val memberRank = Rank(memberRankId, guildId, "Member", 10, emptySet())
        val guild = Guild(
            id = guildId, name = "G", createdAt = Instant.now(),
            homes = GuildHomes(mapOf(homeName to homeWith(homeAllowed)))
        )
        guildRepository = mockk(relaxed = true)
        rankRepository = mockk(relaxed = true)
        memberRepository = mockk(relaxed = true)
        every { guildRepository.getById(guildId) } returns guild
        every { rankRepository.getById(ownerRankId) } returns ownerRank
        every { rankRepository.getById(memberRankId) } returns memberRank
        every { rankRepository.getHighestRank(guildId) } returns ownerRank
        every { memberRepository.getByPlayerAndGuild(ownerPlayerId, guildId) } returns
            Member(ownerPlayerId, guildId, ownerRankId, Instant.now())
        every { memberRepository.getByPlayerAndGuild(memberPlayerId, guildId) } returns
            Member(memberPlayerId, guildId, memberRankId, Instant.now())

        service = GuildServiceBukkit(
            guildRepository = guildRepository,
            rankRepository = rankRepository,
            memberRepository = memberRepository,
            rankService = mockk<RankService>(relaxed = true),
            memberService = mockk<MemberService>(relaxed = true),
            nexoEmojiService = mockk(relaxed = true),
            vaultService = mockk<GuildVaultService>(relaxed = true),
            hologramService = mockk(relaxed = true),
            relationRepository = mockk<RelationRepository>(relaxed = true),
            historyRepository = mockk<MembershipHistoryRepository>(relaxed = true)
        )
    }

    @Test
    fun `owner can use home regardless of whitelist`() {
        setup(homeAllowed = emptySet())
        assertTrue(service.canUseHome(ownerPlayerId, guildId, homeName))
    }

    @Test
    fun `member cannot use home when not whitelisted`() {
        setup(homeAllowed = emptySet())
        assertFalse(service.canUseHome(memberPlayerId, guildId, homeName))
    }

    @Test
    fun `member can use home when whitelisted`() {
        setup(homeAllowed = setOf(memberRankId))
        assertTrue(service.canUseHome(memberPlayerId, guildId, homeName))
    }

    @Test
    fun `non-member cannot use home`() {
        setup(homeAllowed = setOf(memberRankId))
        val outsider = UUID.randomUUID()
        every { memberRepository.getByPlayerAndGuild(outsider, guildId) } returns null
        assertFalse(service.canUseHome(outsider, guildId, homeName))
    }
}
