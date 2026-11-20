package net.lumalyte.lg.application.services

import io.mockk.every
import io.mockk.mockk
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.application.persistence.PrestigeHistory
import net.lumalyte.lg.application.persistence.PrestigeRoleAwardRepository
import net.lumalyte.lg.config.DiscordPrestigeRolesConfig
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.MembershipHistory
import net.lumalyte.lg.domain.rewards.PrestigeRecord
import net.lumalyte.lg.domain.values.PrestigeRoleAward
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals

/** REQ-298: prestige earns the guild's members their tier role; each holds only the highest. */
class PrestigeDiscordRoleServiceTest {
    private val guildId = UUID.randomUUID()
    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()
    private val roles = (1..6).associateWith { "role$it" }

    private class Awards : PrestigeRoleAwardRepository {
        val rows = mutableSetOf<PrestigeRoleAward>()
        override fun record(awards: Collection<PrestigeRoleAward>) = awards.count { a ->
            rows.none { it.playerId == a.playerId && it.guildId == a.guildId && it.tier == a.tier } && rows.add(a)
        }
        override fun highestTier(playerId: UUID) = rows.filter { it.playerId == playerId }.maxOfOrNull { it.tier }
        override fun players() = rows.map { it.playerId }.toSet()
    }

    private class Gateway : DiscordGuildRoleGateway {
        val held = mutableMapOf<UUID, MutableSet<String>>()
        override fun isAvailable() = true
        override fun ensureRole(existingRoleId: String?, roleName: String, allowCreate: Boolean) =
            CompletableFuture.completedFuture<DiscordRoleEnsureResult?>(null)
        override fun grantRole(playerId: UUID, roleId: String): CompletableFuture<DiscordMemberRoleResult> =
            CompletableFuture.completedFuture(
                if (held.getOrPut(playerId) { mutableSetOf() }.add(roleId)) DiscordMemberRoleResult.APPLIED
                else DiscordMemberRoleResult.ALREADY_PRESENT)
        override fun revokeRole(playerId: UUID, roleId: String): CompletableFuture<DiscordMemberRoleResult> =
            CompletableFuture.completedFuture(
                if (held[playerId]?.remove(roleId) == true) DiscordMemberRoleResult.REMOVED
                else DiscordMemberRoleResult.ALREADY_ABSENT)
        override fun revokeRoleByDiscordId(discordId: String, roleId: String) =
            CompletableFuture.completedFuture<DiscordMemberRoleResult>(DiscordMemberRoleResult.ALREADY_ABSENT)
        override fun revokeUnexpectedRoleMembers(roleId: String, allowedPlayerIds: Set<UUID>) =
            CompletableFuture.completedFuture<Int>(0)
        override fun deleteRole(roleId: String) = CompletableFuture.completedFuture<Boolean>(true)
    }

    private fun t(day: Long) = Instant.parse("2026-11-01T00:00:00Z").plusSeconds(day * 86_400)
    private fun member(id: UUID, day: Long) = mockk<Member> { every { playerId } returns id; every { joinedAt } returns t(day) }

    private fun service(
        awards: Awards, gateway: Gateway, members: Set<Member>,
        records: List<PrestigeRecord> = emptyList(), stints: List<MembershipHistory> = emptyList(),
    ): PrestigeDiscordRoleService {
        val config = mockk<ConfigService> {
            every { loadConfig() } returns MainConfig(discordPrestigeRoles = DiscordPrestigeRolesConfig(true, roles, ""))
        }
        val history = object : PrestigeHistory {
            override fun history(guildId: UUID) = records
            override fun firstToReach(tier: Int): PrestigeRecord? = null
        }
        val stintRepo = mockk<MembershipHistoryRepository> { every { getByGuild(any()) } returns stints }
        val memberService = mockk<MemberService> { every { getGuildMembers(guildId) } returns members }
        val guildService = mockk<GuildService> { every { getAllGuilds() } returns setOf(mockk { every { id } returns guildId }) }
        return PrestigeDiscordRoleService(config, awards, history, null, stintRepo, memberService, guildService, gateway, Runnable::run)
    }

    @Test
    fun `members present at a prestige earn it and keep only the highest role`() {
        val awards = Awards(); val gateway = Gateway()
        val svc = service(awards, gateway, setOf(member(alice, 0)))
        svc.prestiged(guildId, 1, t(2)).join()
        assertEquals<Set<String>?>(setOf("role1"), gateway.held[alice])
        svc.prestiged(guildId, 2, t(4)).join()
        assertEquals<Set<String>?>(setOf("role2"), gateway.held[alice])
    }

    @Test
    fun `backfill uses who was in the guild at each past prestige`() {
        val awards = Awards(); val gateway = Gateway()
        val records = listOf(
            PrestigeRecord(UUID.randomUUID(), guildId, alice, 1, "r", t(2)),
            PrestigeRecord(UUID.randomUUID(), guildId, alice, 2, "r", t(6)),
        )
        val stints = listOf(
            MembershipHistory(UUID.randomUUID(), alice, guildId, t(0)),
            MembershipHistory(UUID.randomUUID(), bob, guildId, t(1), t(4)),
        )
        val svc = service(awards, gateway, setOf(member(alice, 0)), records, stints)
        svc.reconcileAll().join()
        assertEquals<Set<String>?>(setOf("role2"), gateway.held[alice])
        assertEquals<Set<String>?>(setOf("role1"), gateway.held[bob])
    }

    @Test
    fun `a tier re-reached later keeps its original roster`() {
        val awards = Awards(); val gateway = Gateway()
        val records = listOf(PrestigeRecord(UUID.randomUUID(), guildId, alice, 1, "r", t(2)))
        val svc = service(awards, gateway, setOf(member(bob, 5)), records)
        svc.prestiged(guildId, 1, t(9)).join()
        assertEquals<Set<String>?>(null, gateway.held[bob])
    }
}
