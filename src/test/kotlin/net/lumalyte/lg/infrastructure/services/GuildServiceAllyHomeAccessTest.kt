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
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class GuildServiceAllyHomeAccessTest {

    private val sourceGuildId = UUID.randomUUID()
    private val targetGuildId = UUID.randomUUID()
    private val ownerRankId = UUID.randomUUID()
    private val memberRankId = UUID.randomUUID()
    private val ownerPlayerId = UUID.randomUUID()
    private val memberPlayerId = UUID.randomUUID()

    private fun makeService(
        targetAllyHome: GuildHome?,
        targetAllowedGuilds: Set<UUID>,
        memberRankPerms: Set<RankPermission> = emptySet(),
        activeAlly: Boolean = true,
        sourceAllyHome: GuildHome? = null
    ): GuildServiceBukkit {
        val ownerRank = Rank(ownerRankId, sourceGuildId, "Owner", 0, RankPermission.values().toSet())
        val memberRank = Rank(memberRankId, sourceGuildId, "Member", 10, memberRankPerms)
        val sourceGuild = Guild(id = sourceGuildId, name = "S", createdAt = Instant.now(), allyHome = sourceAllyHome)
        val targetGuild = Guild(
            id = targetGuildId, name = "T", createdAt = Instant.now(),
            allyHome = targetAllyHome,
            allyHomeAllowedGuilds = targetAllowedGuilds
        )
        val guildRepository = mockk<GuildRepository>(relaxed = true)
        val rankRepository = mockk<RankRepository>(relaxed = true)
        val memberRepository = mockk<MemberRepository>(relaxed = true)
        val relationRepository = mockk<RelationRepository>(relaxed = true)
        every { relationRepository.getByGuildAndType(sourceGuildId, RelationType.ALLY) } returns
            if (activeAlly) setOf(
                Relation.create(
                    guildA = sourceGuildId,
                    guildB = targetGuildId,
                    type = RelationType.ALLY,
                    status = RelationStatus.ACTIVE
                )
            ) else emptySet()
        every { guildRepository.getById(sourceGuildId) } returns sourceGuild
        every { guildRepository.getById(targetGuildId) } returns targetGuild
        every { rankRepository.getById(ownerRankId) } returns ownerRank
        every { rankRepository.getById(memberRankId) } returns memberRank
        every { rankRepository.getHighestRank(sourceGuildId) } returns ownerRank
        every { memberRepository.getByPlayerAndGuild(ownerPlayerId, sourceGuildId) } returns
            Member(ownerPlayerId, sourceGuildId, ownerRankId, Instant.now())
        every { memberRepository.getByPlayerAndGuild(memberPlayerId, sourceGuildId) } returns
            Member(memberPlayerId, sourceGuildId, memberRankId, Instant.now())

        return GuildServiceBukkit(
            guildRepository = guildRepository,
            rankRepository = rankRepository,
            memberRepository = memberRepository,
            rankService = mockk<RankService>(relaxed = true),
            memberService = mockk<MemberService>(relaxed = true),
            nexoEmojiService = mockk(relaxed = true),
            vaultService = mockk<GuildVaultService>(relaxed = true),
            hologramService = mockk(relaxed = true),
            relationRepository = relationRepository,
            historyRepository = mockk<MembershipHistoryRepository>(relaxed = true)
        )
    }

    private val ah = GuildHome(UUID.randomUUID(), Position3D(0, 64, 0))

    @Test
    fun `owner bypasses USE_ALLY_HOMES rank check`() {
        val svc = makeService(targetAllyHome = ah, targetAllowedGuilds = setOf(sourceGuildId))
        assertTrue(svc.canUseAllyHome(ownerPlayerId, sourceGuildId, targetGuildId))
    }

    @Test
    fun `non-owner with USE_ALLY_HOMES and whitelisted source can use`() {
        val svc = makeService(
            targetAllyHome = ah,
            targetAllowedGuilds = setOf(sourceGuildId),
            memberRankPerms = setOf(RankPermission.USE_ALLY_HOMES)
        )
        assertTrue(svc.canUseAllyHome(memberPlayerId, sourceGuildId, targetGuildId))
    }

    @Test
    fun `non-owner without USE_ALLY_HOMES is denied`() {
        val svc = makeService(targetAllyHome = ah, targetAllowedGuilds = setOf(sourceGuildId))
        assertFalse(svc.canUseAllyHome(memberPlayerId, sourceGuildId, targetGuildId))
    }

    @Test
    fun `source not on inbound whitelist is denied`() {
        val svc = makeService(
            targetAllyHome = ah,
            targetAllowedGuilds = emptySet(),
            memberRankPerms = setOf(RankPermission.USE_ALLY_HOMES)
        )
        assertFalse(svc.canUseAllyHome(memberPlayerId, sourceGuildId, targetGuildId))
    }

    @Test
    fun `target without ally home is denied`() {
        val svc = makeService(targetAllyHome = null, targetAllowedGuilds = setOf(sourceGuildId))
        assertFalse(svc.canUseAllyHome(ownerPlayerId, sourceGuildId, targetGuildId))
    }

    @Test
    fun `owner denied when source not on inbound whitelist`() {
        // Whitelist check happens before owner bypass — keep that ordering invariant locked in.
        val svc = makeService(targetAllyHome = ah, targetAllowedGuilds = emptySet())
        assertFalse(svc.canUseAllyHome(ownerPlayerId, sourceGuildId, targetGuildId))
    }

    @Test
    fun `whitelist without active alliance is denied`() {
        val svc = makeService(
            targetAllyHome = ah,
            targetAllowedGuilds = setOf(sourceGuildId),
            memberRankPerms = setOf(RankPermission.USE_ALLY_HOMES),
            activeAlly = false
        )
        assertFalse(svc.canUseAllyHome(memberPlayerId, sourceGuildId, targetGuildId))
    }

    // --- canUseOwnAllyHome: a member teleporting to their OWN guild's ally home ---

    @Test
    fun `own ally home - owner can use without USE_ALLY_HOMES`() {
        val svc = makeService(targetAllyHome = null, targetAllowedGuilds = emptySet(), sourceAllyHome = ah)
        assertTrue(svc.canUseOwnAllyHome(ownerPlayerId, sourceGuildId))
    }

    @Test
    fun `own ally home - member with USE_ALLY_HOMES can use`() {
        val svc = makeService(
            targetAllyHome = null, targetAllowedGuilds = emptySet(),
            memberRankPerms = setOf(RankPermission.USE_ALLY_HOMES),
            sourceAllyHome = ah
        )
        assertTrue(svc.canUseOwnAllyHome(memberPlayerId, sourceGuildId))
    }

    @Test
    fun `own ally home - member without USE_ALLY_HOMES is denied`() {
        val svc = makeService(targetAllyHome = null, targetAllowedGuilds = emptySet(), sourceAllyHome = ah)
        assertFalse(svc.canUseOwnAllyHome(memberPlayerId, sourceGuildId))
    }

    @Test
    fun `own ally home - denied when no ally home set`() {
        val svc = makeService(targetAllyHome = null, targetAllowedGuilds = emptySet(), sourceAllyHome = null)
        assertFalse(svc.canUseOwnAllyHome(ownerPlayerId, sourceGuildId))
    }
}
