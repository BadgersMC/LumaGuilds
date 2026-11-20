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
        sourceAllyHome: GuildHome? = null,
        access: net.lumalyte.lg.domain.values.AllyHomeAccess = net.lumalyte.lg.domain.values.AllyHomeAccess(),
        secondHome: GuildHome? = null,
        targetPrestige: Int = 0,
        outbound: Map<UUID, Set<UUID>> = emptyMap(),
        waypoints: Set<UUID> = setOf(sourceGuildId, targetGuildId),
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
            historyRepository = mockk<MembershipHistoryRepository>(relaxed = true),
            adminOverrideService = mockk(relaxed = true),
            homeActivationService = mockk(relaxed = true),
            allyHomesUnlocked = { it in waypoints },
            allyHomeAccess = accessRepository.also {
                every { it.access(targetGuildId) } returns access
                every { it.secondHome(targetGuildId) } returns secondHome
                every { it.outboundRanks(sourceGuildId) } returns outbound
            },
            prestigePerks = net.lumalyte.lg.application.services.GuildPrestigePerks(
                mockk<net.lumalyte.lg.application.services.GuildPrestigeStandings>().also {
                    every { it.standing(any()) } returns net.lumalyte.lg.domain.rewards.PrestigeStanding(targetPrestige, null, 0)
                },
            ) { net.lumalyte.lg.domain.rewards.PrestigePerks(net.lumalyte.lg.config.PrestigePerksConfig()) },
            homeAccess = LegacyHomeAccessTestAdapter,
        )
    }

    private val accessRepository = mockk<net.lumalyte.lg.application.persistence.AllyHomeAccessRepository>(relaxed = true)

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

    @Test
    fun `own ally home - denied for non-member`() {
        // Locks in the access boundary: a player who isn't in the guild at all must be denied
        // regardless of any other state. memberRepository returns null, so this exercises the
        // pre-rank early-return that prevents unauthenticated access to the ally home.
        val svc = makeService(targetAllyHome = null, targetAllowedGuilds = emptySet(), sourceAllyHome = ah)
        val nonMember = UUID.randomUUID()
        assertFalse(svc.canUseOwnAllyHome(nonMember, sourceGuildId))
    }

    // REQ-182: the host's own rules.
    private val usable = setOf(RankPermission.USE_ALLY_HOMES)

    @Test
    fun `a player the host blocked is refused, even the ally's owner`() {
        val access = net.lumalyte.lg.domain.values.AllyHomeAccess(blockedPlayers = setOf(ownerPlayerId))
        val svc = makeService(targetAllyHome = ah, targetAllowedGuilds = setOf(sourceGuildId), access = access)
        assertFalse(svc.canUseAllyHome(ownerPlayerId, sourceGuildId, targetGuildId))
    }

    @Test
    fun `host-picked ranks admit only those ranks`() {
        val access = net.lumalyte.lg.domain.values.AllyHomeAccess(visitorRanks = mapOf(sourceGuildId to setOf(ownerRankId)))
        val svc = makeService(ah, setOf(sourceGuildId), usable, access = access)
        assertTrue(svc.canUseAllyHome(ownerPlayerId, sourceGuildId, targetGuildId))
        assertFalse(svc.canUseAllyHome(memberPlayerId, sourceGuildId, targetGuildId))
    }

    @Test
    fun `a picked rank still needs Use ally homes in its own guild`() {
        val access = net.lumalyte.lg.domain.values.AllyHomeAccess(visitorRanks = mapOf(sourceGuildId to setOf(memberRankId)))
        val svc = makeService(ah, setOf(sourceGuildId), emptySet(), access = access)
        assertFalse(svc.canUseAllyHome(memberPlayerId, sourceGuildId, targetGuildId))
    }

    // REQ-180: the second allied home.
    @Test
    fun `the second allied home is usable only at Prestige II with a home set`() {
        val second = GuildHome(UUID.randomUUID(), Position3D(5, 70, 5))
        assertFalse(makeService(ah, setOf(sourceGuildId), usable, secondHome = second, targetPrestige = 1)
            .canUseAllyHome(memberPlayerId, sourceGuildId, targetGuildId, slot = 2))
        assertFalse(makeService(ah, setOf(sourceGuildId), usable, secondHome = null, targetPrestige = 2)
            .canUseAllyHome(memberPlayerId, sourceGuildId, targetGuildId, slot = 2))
        val svc = makeService(ah, setOf(sourceGuildId), usable, secondHome = second, targetPrestige = 2)
        assertTrue(svc.canUseAllyHome(memberPlayerId, sourceGuildId, targetGuildId, slot = 2))
        assertEquals(second, svc.getAllyHome(targetGuildId, slot = 2))
    }

    @Test
    fun `the second home follows the same host rules`() {
        val second = GuildHome(UUID.randomUUID(), Position3D(5, 70, 5))
        val access = net.lumalyte.lg.domain.values.AllyHomeAccess(blockedPlayers = setOf(memberPlayerId))
        val svc = makeService(ah, setOf(sourceGuildId), usable, access = access, secondHome = second, targetPrestige = 2)
        assertFalse(svc.canUseAllyHome(memberPlayerId, sourceGuildId, targetGuildId, slot = 2))
    }

    // REQ-183: the visitor's own guild picks its ranks per ally.
    @Test
    fun `an outbound pick lets a rank without Use ally homes visit that ally`() {
        val svc = makeService(ah, setOf(sourceGuildId), emptySet(), outbound = mapOf(targetGuildId to setOf(memberRankId)))
        assertTrue(svc.canUseAllyHome(memberPlayerId, sourceGuildId, targetGuildId))
    }

    @Test
    fun `an outbound pick can keep a rank with Use ally homes away from one ally`() {
        val svc = makeService(ah, setOf(sourceGuildId), usable, outbound = mapOf(targetGuildId to emptySet()))
        assertFalse(svc.canUseAllyHome(memberPlayerId, sourceGuildId, targetGuildId))
        assertTrue(svc.canUseAllyHome(ownerPlayerId, sourceGuildId, targetGuildId))
    }

    @Test
    fun `a pick for another ally does not apply`() {
        val svc = makeService(ah, setOf(sourceGuildId), usable, outbound = mapOf(UUID.randomUUID() to emptySet()))
        assertTrue(svc.canUseAllyHome(memberPlayerId, sourceGuildId, targetGuildId))
    }

    // Setting and removing allied homes needs Manage home, like every other home.
    @Test
    fun `Manage home is enough to set and remove an allied home`() {
        every { accessRepository.setSecondHome(any(), any()) } returns true
        every { accessRepository.secondHome(sourceGuildId) } returns ah
        val svc = makeService(ah, setOf(sourceGuildId), setOf(RankPermission.MANAGE_HOME), targetPrestige = 2)
        assertTrue(svc.setAllyHome(sourceGuildId, ah, memberPlayerId, slot = 2))
        assertTrue(svc.removeAllyHome(sourceGuildId, memberPlayerId, slot = 2))
    }

    @Test
    fun `Manage members alone no longer sets an allied home`() {
        every { accessRepository.setSecondHome(any(), any()) } returns true
        val svc = makeService(ah, setOf(sourceGuildId), setOf(RankPermission.MANAGE_MEMBERS), targetPrestige = 2)
        assertFalse(svc.setAllyHome(sourceGuildId, ah, memberPlayerId, slot = 2))
    }

    // Audit 2026-11-10: commands skipped the Allied Waypoints reward the menu requires.
    @Test
    fun `allied homes need Allied Waypoints on both guilds`() {
        assertFalse(makeService(ah, setOf(sourceGuildId), usable, waypoints = setOf(targetGuildId))
            .canUseAllyHome(memberPlayerId, sourceGuildId, targetGuildId))
        assertFalse(makeService(ah, setOf(sourceGuildId), usable, waypoints = setOf(sourceGuildId))
            .canUseAllyHome(memberPlayerId, sourceGuildId, targetGuildId))
        assertTrue(makeService(ah, setOf(sourceGuildId), usable).canUseAllyHome(memberPlayerId, sourceGuildId, targetGuildId))
    }

    @Test
    fun `setting an allied home needs Allied Waypoints`() {
        every { accessRepository.setSecondHome(any(), any()) } returns true
        val locked = makeService(ah, setOf(sourceGuildId), setOf(RankPermission.MANAGE_HOME), targetPrestige = 2, waypoints = emptySet())
        assertFalse(locked.setAllyHome(sourceGuildId, ah, memberPlayerId, slot = 2))
    }

    // Audit 2026-11-10: a guild with only its second allied home set was not listed.
    @Test
    fun `an ally with only a second allied home is still listed`() {
        val second = GuildHome(UUID.randomUUID(), Position3D(5, 70, 5))
        val svc = makeService(targetAllyHome = null, targetAllowedGuilds = setOf(sourceGuildId), usable,
            secondHome = second, targetPrestige = 2)
        val entries = svc.getAllyHomeEntries(sourceGuildId)
        assertEquals(listOf(2 to second), entries.map { it.slot to it.home })
    }
}
